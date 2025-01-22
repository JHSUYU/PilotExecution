package edu.uva.liftlab.recoverychecker.distributedtracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootClass;
import soot.SootMethod;

import static edu.uva.liftlab.recoverychecker.util.Constants.INSTRUMENTATION_SUFFIX;

public class BaggagePropagation {
    SootClass sootClass;

    RunnableParameterWrapper runnableParameterWrapper;

    FutureCallbackParameterWrapper futureCallbackParameterWrapper;

    GoogleConcurrentAsyncFunctionParameterWrapper googleConcurrentAsyncFunctionParameterWrapper;

    CallablePropagator callablePropagator;

    ExecutorPropagator executorPropagator;

    FuturePropagator futurePropagator;
    private static final Logger LOG = LoggerFactory.getLogger(BaggagePropagation.class);


    public BaggagePropagation(SootClass sootClass){
        this.sootClass = sootClass;
        this.runnableParameterWrapper = new RunnableParameterWrapper(sootClass);
        this.futureCallbackParameterWrapper = new FutureCallbackParameterWrapper(sootClass);
        this.googleConcurrentAsyncFunctionParameterWrapper = new GoogleConcurrentAsyncFunctionParameterWrapper(sootClass);
        this.callablePropagator = new CallablePropagator(sootClass);
        this.executorPropagator = new ExecutorPropagator(sootClass);
        this.futurePropagator = new FuturePropagator(sootClass);
    }


    public void propagateBaggage() {
        this.runnableParameterWrapper.wrapParameter();
        this.futureCallbackParameterWrapper.wrapParameter();
        this.googleConcurrentAsyncFunctionParameterWrapper.wrapParameter();

        this.futurePropagator.wrapFutureTaskParameter();
        this.executorPropagator.wrapExecutorParameter();
        LOG.info("Finished propagating baggage for class: {}", sootClass.getName());
    }
}
