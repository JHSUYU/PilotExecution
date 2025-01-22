package edu.uva.liftlab.recoverychecker.tainting.tracetainting;

import soot.BooleanType;
import soot.Modifier;
import soot.SootClass;
import soot.SootField;

import static edu.uva.liftlab.recoverychecker.util.SootUtils.classShouldBeInstrumented;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.getDryRunTraceFieldName;

public class InjectDryRunTrace {
    public SootClass sootClass;

    public static final String NEED_DRY_RUN_TRACE = "needDryRunTrace$";

    public void addNeedDryRunTraceField() {
        if(!classShouldBeInstrumented(sootClass)) {
            return;
        }
        SootField field = new SootField(getDryRunTraceFieldName(sootClass),
                BooleanType.v(),
                Modifier.PUBLIC);

        if (!sootClass.declaresField(NEED_DRY_RUN_TRACE)) {
            sootClass.addField(field);
        }
    }
    public InjectDryRunTrace(SootClass sootClass) {
        this.sootClass = sootClass;
    }
}
