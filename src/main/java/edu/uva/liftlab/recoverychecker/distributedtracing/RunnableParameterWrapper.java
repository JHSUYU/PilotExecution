package edu.uva.liftlab.recoverychecker.distributedtracing;

import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import edu.uva.liftlab.recoverychecker.util.SootUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RunnableParameterWrapper extends ParameterWrapper{

    private static final Logger LOG = LoggerFactory.getLogger(RunnableParameterWrapper.class);
    public RunnableParameterWrapper(SootClass sootClass){
        super(sootClass);
    }

    private boolean implementsRunnable(SootClass cls) {
        if (cls.getName().equals("java.lang.Runnable")) {
            return true;
        }

        for (SootClass iface : cls.getInterfaces()) {
            if (implementsRunnable(iface)) {
                return true;
            }
        }

        if (cls.hasSuperclass()) {
            return implementsRunnable(cls.getSuperclass());
        }

        return false;
    }

    public void wrapParameter() {
        if (!implementsRunnable(sootClass)) {
            return;
        }

        SootMethod runMethod;
        try {
            runMethod = sootClass.getMethod("run", Collections.emptyList(), VoidType.v());
        } catch (RuntimeException e) {
            LOG.warn("No run method found in class: {}", sootClass.getName());
            return;
        }

        wrapInterface(runMethod);
    }
}
