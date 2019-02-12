#!/usr/bin/env python
# -*- encoding: utf-8 -*-
#
# This file is auto-generated by h2o-3/h2o-bindings/bin/gen_python.py
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.estimators.estimator_base import H2OEstimator
from h2o.exceptions import H2OValueError
from h2o.frame import H2OFrame
from h2o.utils.typechecks import assert_is_type, Enum, numeric


class H2OMojodelegatingEstimator(H2OEstimator):
    """
    Mojo Delegating Model

    """

    algo = "mojodelegating"

    def __init__(self, **kwargs):
        super(H2OMojodelegatingEstimator, self).__init__()
        self._parms = {}
        names_list = {"mojo_key"}
        if "Lambda" in kwargs: kwargs["lambda_"] = kwargs.pop("Lambda")
        for pname, pvalue in kwargs.items():
            if pname == 'model_id':
                self._id = pvalue
                self._parms["model_id"] = pvalue
            elif pname in names_list:
                # Using setattr(...) will invoke type-checking of the arguments
                setattr(self, pname, pvalue)
            else:
                raise H2OValueError("Unknown parameter %s = %r" % (pname, pvalue))

    @property
    def mojo_key(self):
        """
        Key to an uploaded MOJO archive frame

        Type: ``H2OFrame``.
        """
        return self._parms.get("mojo_key")

    @mojo_key.setter
    def mojo_key(self, mojo_key):
        assert_is_type(mojo_key, None, H2OFrame)
        self._parms["mojo_key"] = mojo_key


