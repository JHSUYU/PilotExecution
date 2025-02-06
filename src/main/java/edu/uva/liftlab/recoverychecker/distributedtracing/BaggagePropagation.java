package edu.uva.liftlab.recoverychecker.distributedtracing;

import edu.uva.liftlab.recoverychecker.isolation.stateredirection.ClassFilterHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootClass;

public class BaggagePropagation {
    SootClass sootClass;

    RunnableParameterWrapper runnableParameterWrapper;

    FutureCallbackParameterWrapper futureCallbackParameterWrapper;

    GoogleConcurrentAsyncFunctionParameterWrapper googleConcurrentAsyncFunctionParameterWrapper;

    CallablePropagator callablePropagator;

    ExecutorPropagator executorPropagator;

    FuturePropagator futurePropagator;

    ClassFilterHelper classFilterHelper;
    private static final Logger LOG = LoggerFactory.getLogger(BaggagePropagation.class);


    public BaggagePropagation(SootClass sootClass, ClassFilterHelper filterHelper){
        this.sootClass = sootClass;
        this.classFilterHelper = filterHelper;
        this.runnableParameterWrapper = new RunnableParameterWrapper(sootClass);
        this.futureCallbackParameterWrapper = new FutureCallbackParameterWrapper(sootClass);
        this.googleConcurrentAsyncFunctionParameterWrapper = new GoogleConcurrentAsyncFunctionParameterWrapper(sootClass);
        this.callablePropagator = new CallablePropagator(sootClass);
        this.executorPropagator = new ExecutorPropagator(sootClass, classFilterHelper);
        this.futurePropagator = new FuturePropagator(sootClass);
    }


    public void propagateBaggage() {
        //this.runnableParameterWrapper.wrapParameter();
//        this.futureCallbackParameterWrapper.wrapParameter();
//        this.googleConcurrentAsyncFunctionParameterWrapper.wrapParameter();

        this.futurePropagator.propagateContext();
        this.executorPropagator.propagateContext();
        LOG.info("Finished propagating baggage for class: {}", sootClass.getName());
    }
}
