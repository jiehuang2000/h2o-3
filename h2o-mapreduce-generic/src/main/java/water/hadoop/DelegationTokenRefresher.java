package water.hadoop;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import water.H2O;
import water.MRTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static water.hadoop.h2omapper.*;

public class DelegationTokenRefresher implements Runnable {
  
  public static final String KEYTAB_FILE = "hive_keytab";
  private static final String KEYTAB_PATH = "./" + KEYTAB_FILE;

  public static void setup(Configuration conf) {
    String hiveHost = conf.get(H2O_HIVE_HOST);
    String hivePrincipal = conf.get(H2O_HIVE_PRINCIPAL);
    String authPrincipal = conf.get(H2O_AUTH_PRINCIPAL);

    if (authPrincipal != null && hiveHost != null && hivePrincipal != null) {
      new DelegationTokenRefresher(authPrincipal, hiveHost, hivePrincipal).start();
    } else {
      log("Delegation token refresh not active.", null);
    }
  }

  private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setDaemon(true).setNameFormat("delegation-token-refresher-%d").build()
  );

  private final String _authPrincipal;
  private final String _hiveHost;
  private final String _hivePrincipal;

  private final HiveTokenGenerator _hiveTokenGenerator = new HiveTokenGenerator();

  public DelegationTokenRefresher(
      String authPrincipal,
      String hiveHost,
      String hivePrincipal
  ) {
    this._authPrincipal = authPrincipal;
    this._hiveHost = hiveHost;
    this._hivePrincipal = hivePrincipal;
  }

  public void start() {
    if (_hiveTokenGenerator.isHiveDriverPresent()) {
      _executor.scheduleAtFixedRate(this, 0, 1, TimeUnit.MINUTES);
    }
  }

  private static void log(String s, Exception e) {
    System.out.println("TOKEN REFRESH: " + s);
    if (e != null) {
      e.printStackTrace(System.out);
    }
  }

  @Override
  public void run() {
    boolean leader = (H2O.CLOUD.size() > 0 && H2O.CLOUD.leader() == H2O.SELF);
    if (leader) {
      refreshTokens();
    }
  }
  
  private void refreshTokens() {
    try {
      doRefreshTokens();
    } catch (IOException | InterruptedException e) {
      log("Failed to refresh token.", e);
    }
  }
  
  private static class DistributeCreds extends MRTask {
    
    private final byte[] _credsSerialized;

    private DistributeCreds(byte[] credsSerialized) {
      this._credsSerialized = credsSerialized;
    }

    @Override
    protected void setupLocal() {
      try {
        Credentials creds = deserialize();
        log("Updating credentials", null);
        UserGroupInformation.getCurrentUser().addCredentials(creds);
      } catch (IOException e) {
        log("Failed to update credentials", e);
      }
    }

    private Credentials deserialize() throws IOException {
      ByteArrayInputStream tokensBuf = new ByteArrayInputStream(_credsSerialized);
      Credentials creds = new Credentials();
      creds.readTokenStorageStream(new DataInputStream(tokensBuf));
      return creds;
    }
  }
  
  private Credentials getTokens(UserGroupInformation ugi) throws IOException, InterruptedException {
    Credentials creds = new Credentials();
    _hiveTokenGenerator.addHiveDelegationTokenAsUser(ugi, _hiveHost, _hivePrincipal, creds);
    return creds;
  }
  
  private void distribute(Credentials creds) {
    try {
      byte[] credsSerialized = serializeCreds(creds);
      new DistributeCreds(credsSerialized).doAllNodes();
    } catch (IOException e) {
      log("Failed to serialize credentials", e);
    }

  }

  private void doRefreshTokens() throws IOException, InterruptedException {
    log("Log in from keytab", null);
    UserGroupInformation ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(_authPrincipal, KEYTAB_PATH);
    Credentials creds = getTokens(ugi);
    distribute(creds);
  }

  private byte[] serializeCreds(Credentials creds) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    DataOutputStream dataStream = new DataOutputStream(byteStream);
    creds.writeTokenStorageToStream(dataStream);
    return byteStream.toByteArray();
  }

}
