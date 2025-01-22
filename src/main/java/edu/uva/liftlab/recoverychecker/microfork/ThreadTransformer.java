package edu.uva.liftlab.recoverychecker.microfork;

import edu.uva.liftlab.recoverychecker.generator.FastForwardMethodGenerator;
import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.tagkit.Tag;
import soot.util.Chain;

import java.util.*;

import static edu.uva.liftlab.recoverychecker.transformer.DryRunTransformer.SET_BY_DRY_RUN;
import static edu.uva.liftlab.recoverychecker.util.Constants.*;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.*;


public class ThreadTransformer {
    SootClass originalClass;

    FastForwardMethodGenerator fastForwardMethodGenerator;

    LockAnalyzer lockAnalyzer;

    private static final Logger LOG = LoggerFactory.getLogger(ThreadTransformer.class);

    private Map<SootMethod, List<Unit>> methodToGotoUnits = new HashMap<>();

    public ThreadTransformer(SootClass sootClass) {
        this.originalClass = sootClass;
        this.fastForwardMethodGenerator = new FastForwardMethodGenerator();
    }
    public void addShadowField() {
        SootField shadowField = new SootField(SHADOW_FIELD,
                BooleanType.v(),
                Modifier.PUBLIC);

        if (!originalClass.declaresField("isShadow", BooleanType.v())) {
            originalClass.addField(shadowField);
        }
    }

    private Local getWrappedValue(List<Unit> code, LocalGeneratorUtil lg, Local stateMap, Local local, String wrapperClassName) {
        Local tempResult = lg.generateLocalWithId(RefType.v(WRAP_CONTEXT_CLASS_NAME), "temp");

        // 添加中间变量存储 HashMap.get 的 Object 结果
        Local objectResult = lg.generateLocalWithId(RefType.v("java.lang.Object"), "$objectResult");

        VirtualInvokeExpr getExpr = Jimple.v().newVirtualInvokeExpr(
                stateMap,
                Scene.v().makeMethodRef(
                        Scene.v().getSootClass("java.util.HashMap"),
                        "get",
                        Arrays.asList(RefType.v("java.lang.Object")),
                        RefType.v("java.lang.Object"),
                        false
                ),
                Collections.singletonList(StringConstant.v(local.getName()))
        );

        // 先把 HashMap.get 结果存为 Object
        code.add(Jimple.v().newAssignStmt(objectResult, getExpr));

        // 将 Object 转换为 WrapContext
        code.add(Jimple.v().newAssignStmt(
                tempResult,
                Jimple.v().newCastExpr(
                        objectResult,
                        RefType.v(WRAP_CONTEXT_CLASS_NAME)
                )
        ));

        Local wrappedValue = lg.generateLocalWithId(RefType.v(wrapperClassName), "$wrapped");
        Local objectValue = lg.generateLocalWithId(RefType.v("java.lang.Object"), "$objectValue");
        code.add(Jimple.v().newAssignStmt(
                objectValue,
                Jimple.v().newVirtualInvokeExpr(
                        tempResult,
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(WRAP_CONTEXT_CLASS_NAME),
                                "getValue",
                                Collections.emptyList(),  // getValue 方法没有参数
                                RefType.v("java.lang.Object"),  // 返回类型是 Object
                                false
                        )
                )
        ));
        code.add(Jimple.v().newAssignStmt(
                wrappedValue,
                Jimple.v().newCastExpr(
                        objectValue,
                        RefType.v(wrapperClassName)
                )
        ));
        return wrappedValue;
    }

    private List<Unit> createRestoreCode(Body body, SootMethod method, List<Local> originalLocals){
        List<Unit> code = new ArrayList<>();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        Local stateMap = lg.generateLocalWithId(RefType.v("java.util.HashMap"), "$state");

        code.add(Jimple.v().newAssignStmt(
                stateMap,
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(UTIL_CLASS_NAME),
                                "getState",
                                Arrays.asList(RefType.v("java.lang.String")),
                                RefType.v("java.util.HashMap"),
                                true
                        ),
                        Collections.singletonList(
                                StringConstant.v(body.getMethod().getSignature())
                        )
                )
        ));



        for(Local local: originalLocals){
            Type type = local.getType();

            if(type instanceof PrimType){
                if(type instanceof IntType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Integer"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Integer"),
                                            "intValue",
                                            Collections.emptyList(),
                                            IntType.v(),
                                            false
                                    )
                            )
                    ));
                }else if(type instanceof BooleanType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Boolean"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Boolean"),
                                            "booleanValue",
                                            Collections.emptyList(),
                                            BooleanType.v(),
                                            false
                                    )
                            )
                    ));
                }else if(type instanceof FloatType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Float"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Float"),
                                            "floatValue",
                                            Collections.emptyList(),
                                            FloatType.v(),
                                            false
                                    )
                            )
                    ));
                } else if(type instanceof DoubleType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Double"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Double"),
                                            "doubleValue",
                                            Collections.emptyList(),
                                            DoubleType.v(),
                                            false
                                    )
                            )
                    ));
                } else if(type instanceof LongType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Long"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Long"),
                                            "longValue",
                                            Collections.emptyList(),
                                            LongType.v(),
                                            false
                                    )
                            )
                    ));
                } else if(type instanceof ByteType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Byte"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Byte"),
                                            "byteValue",
                                            Collections.emptyList(),
                                            ByteType.v(),
                                            false
                                    )
                            )
                    ));
                } else if(type instanceof ShortType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Short"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Short"),
                                            "shortValue",
                                            Collections.emptyList(),
                                            ShortType.v(),
                                            false
                                    )
                            )
                    ));
                }else if(type instanceof CharType) {
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Character"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Character"),
                                            "charValue",
                                            Collections.emptyList(),
                                            CharType.v(),
                                            false
                                    )
                            )
                    ));
                }
            } else {
                String typeName;
                if(type instanceof RefType) {
                    typeName = ((RefType)type).getClassName();
                } else if(type instanceof ArrayType) {
                    typeName = "java.lang.Object";

                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newCastExpr(
                                    getWrappedValue(code, lg, stateMap, local, typeName),
                                    type
                            )
                    ));
                    continue;
                } else {
                    // 其他特殊类型情况，暂时使用 Object
                    typeName = "java.lang.Object";
                }

                code.add(Jimple.v().newAssignStmt(
                        local,
                        getWrappedValue(code, lg, stateMap, local, typeName)
                ));
            }
        }

        return code;
    }

    private List<Unit> createFieldRestoreCode(Body body, SootMethod method) {
        List<Unit> code = new ArrayList<>();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        Local stateMap = lg.generateLocalWithId(RefType.v("java.util.HashMap"), "$fieldState");
        code.add(Jimple.v().newAssignStmt(
                stateMap,
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(UTIL_CLASS_NAME),
                                "getFieldState",
                                Arrays.asList(RefType.v("java.lang.String")),
                                RefType.v("java.util.HashMap"),
                                true
                        ),
                        Collections.singletonList(
                                StringConstant.v(body.getMethod().getSignature())
                        )
                )
        ));

        Local thisRef = null;
        boolean isStatic = body.getMethod().isStatic();

        if(!isStatic) {
            thisRef = body.getThisLocal();
        }
        Chain<SootField> fields = body.getMethod().getDeclaringClass().getFields();

        for(SootField field : fields) {
            Type type = field.getType();

            if(field.getName().contains(IS_FAST_FORWARD_BAGGAGE)){
                continue;
            }

            if(field.isStatic()){
                processField(field, null, type, code, lg, stateMap);
            } else {
                if(isStatic){
                    continue;
                }

                if(thisRef == null){
                    throw new RuntimeException("thisRef is null");
                }

                processField(field, thisRef, type, code, lg, stateMap);
            }
        }

        return code;
    }

    private Local getWrappedValue(List<Unit> code, LocalGeneratorUtil lg, Local stateMap, SootField field, String wrapperClassName) {
        Local tempResult = lg.generateLocalWithId(RefType.v(WRAP_CONTEXT_CLASS_NAME), "temp");

        Local objectResult = lg.generateLocalWithId(RefType.v("java.lang.Object"), "$objectResult");

        VirtualInvokeExpr getExpr = Jimple.v().newVirtualInvokeExpr(
                stateMap,
                Scene.v().makeMethodRef(
                        Scene.v().getSootClass("java.util.HashMap"),
                        "get",
                        Arrays.asList(RefType.v("java.lang.Object")),
                        RefType.v("java.lang.Object"),
                        false
                ),
                Collections.singletonList(StringConstant.v(field.getName()))
        );

        code.add(Jimple.v().newAssignStmt(objectResult, getExpr));

        code.add(Jimple.v().newAssignStmt(
                tempResult,
                Jimple.v().newCastExpr(
                        objectResult,
                        RefType.v(WRAP_CONTEXT_CLASS_NAME)
                )
        ));


        Local wrappedValue = lg.generateLocalWithId(RefType.v(wrapperClassName), "$wrapped");
        Local objectValue = lg.generateLocalWithId(RefType.v("java.lang.Object"), "$objectValue");
        code.add(Jimple.v().newAssignStmt(
                objectValue,
                Jimple.v().newInstanceFieldRef(
                        tempResult,
                        Scene.v().makeFieldRef(
                                Scene.v().getSootClass(WRAP_CONTEXT_CLASS_NAME),
                                "value",
                                RefType.v("java.lang.Object"),
                                false
                        )
                )
        ));

        // 然后做类型转换
        code.add(Jimple.v().newAssignStmt(
                wrappedValue,
                Jimple.v().newCastExpr(
                        objectValue,
                        RefType.v(wrapperClassName)
                )
        ));
        return wrappedValue;
    }

    public SootMethod instrumentShadowVersionMethods(){
        SootClass targetClass = Scene.v().getSootClass("org.apache.hadoop.hbase.master.assignment.AssignmentManager");
        SootMethod originalMethod = targetClass.getMethodByName("waitOnAssignQueue");
        Body originalBody = originalMethod.retrieveActiveBody();

        SootMethod shadowMethod = targetClass.getMethodByName("waitOnAssignQueue$shadow");

        Body newBody = shadowMethod.retrieveActiveBody();

        SootField originalField = originalMethod.getDeclaringClass().getFieldByName("pendingAssignQueue");

        String newFieldName = "pendingAssignQueue$dryrun";
        SootField newField = null;
        try {
            newField = originalMethod.getDeclaringClass().getFieldByName(newFieldName);
        } catch (RuntimeException e) {
            // 如果字段不存在，创建一个新的，保持和原始字段相同的类型和修饰符
            newField = new SootField(
                    newFieldName,
                    originalField.getType(),
                    originalField.getModifiers()
            );
            originalMethod.getDeclaringClass().addField(newField);
        }


        for (Unit unit : newBody.getUnits()) {
            for (ValueBox valueBox : unit.getUseAndDefBoxes()) {
                Value value = valueBox.getValue();
                if (value instanceof FieldRef) {
                    FieldRef fieldRef = (FieldRef) value;
                    if (fieldRef.getField().equals(originalField)) {
                        // 创建新的字段引用
                        if (fieldRef instanceof StaticFieldRef) {
                            valueBox.setValue(Jimple.v().newStaticFieldRef(newField.makeRef()));
                        } else if (fieldRef instanceof InstanceFieldRef) {
                            InstanceFieldRef instanceRef = (InstanceFieldRef) fieldRef;
                            valueBox.setValue(
                                    Jimple.v().newInstanceFieldRef(
                                            instanceRef.getBase(),
                                            newField.makeRef()
                                    )
                            );
                        }
                    }
                }
            }
        }

        shadowMethod.setActiveBody(newBody);

        return shadowMethod;
    }

    private void processField(SootField field, Local thisRef, Type type, List<Unit> code,
                              LocalGeneratorUtil lg, Local stateMap) {
        if(type instanceof PrimType) {
            if(type instanceof IntType) {
                Local tempValue = lg.generateLocalWithId(IntType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Integer"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Integer"),
                                        "intValue",
                                        Collections.emptyList(),
                                        IntType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof BooleanType) {
                Local tempValue = lg.generateLocalWithId(BooleanType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Boolean"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Boolean"),
                                        "booleanValue",
                                        Collections.emptyList(),
                                        BooleanType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof FloatType) {
                Local tempValue = lg.generateLocalWithId(FloatType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Float"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Float"),
                                        "floatValue",
                                        Collections.emptyList(),
                                        FloatType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof DoubleType) {
                Local tempValue = lg.generateLocalWithId(DoubleType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Double"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Double"),
                                        "doubleValue",
                                        Collections.emptyList(),
                                        DoubleType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof LongType) {
                Local tempValue = lg.generateLocalWithId(LongType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Long"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Long"),
                                        "longValue",
                                        Collections.emptyList(),
                                        LongType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof ByteType) {
                Local tempValue = lg.generateLocalWithId(ByteType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Byte"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Byte"),
                                        "byteValue",
                                        Collections.emptyList(),
                                        ByteType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof ShortType) {
                Local tempValue = lg.generateLocalWithId(ShortType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Short"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Short"),
                                        "shortValue",
                                        Collections.emptyList(),
                                        ShortType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof CharType) {
                Local tempValue = lg.generateLocalWithId(CharType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Character"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Character"),
                                        "charValue",
                                        Collections.emptyList(),
                                        CharType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            }
        } else {
            String typeName;
            if(type instanceof RefType) {
                typeName = ((RefType)type).getClassName();

                Local tempValue = lg.generateLocalWithId(type, "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        getWrappedValue(code, lg, stateMap, field, typeName)
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));

            } else if(type instanceof ArrayType) {
                typeName = "java.lang.Object";

                Local tempValue = lg.generateLocalWithId(type, "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newCastExpr(
                                getWrappedValue(code, lg, stateMap, field, typeName),
                                type
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));

            } else {
                // 其他特殊类型情况，使用 Object
                typeName = "java.lang.Object";

                Local tempValue = lg.generateLocalWithId(type, "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        getWrappedValue(code, lg, stateMap, field, typeName)
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            }
        }
    }

    private void addIsShadowField(SootClass sootClass) {
        String newFieldName = SHADOW_FIELD;

        if(sootClass.declaresFieldByName(newFieldName)) {
            return;
        }

        // Create a new boolean field with public modifier
        SootField newField = new SootField(
                newFieldName,
                BooleanType.v(),  // Use boolean type
                Modifier.PUBLIC   // Set modifier to public
        );

        try {
            sootClass.addField(newField);
            //LOG.info("Added shadow field {} to class {}", newFieldName, sootClass.getName());
        } catch (Exception e) {
            LOG.error("Failed to add field {} to class {}: {}",
                    newFieldName, sootClass.getName(), e.getMessage());
        }
    }

    private FieldRef createFieldRef(SootField field, Local thisRef) {
        return field.isStatic()
                ? Jimple.v().newStaticFieldRef(field.makeRef())
                : Jimple.v().newInstanceFieldRef(thisRef, field.makeRef());
    }

    public void redirectToShadowRun(SootClass sootClass) {
        addIsShadowField(sootClass);
        SootMethod runMethod = sootClass.getMethodByName("run");
        Body body = runMethod.retrieveActiveBody();
        Chain<Unit> units = body.getUnits();
        Unit lastIdentityStmt = getLastIdentityStmt(body);

        Local conditionLocal = Jimple.v().newLocal("$isShadow", BooleanType.v());
        body.getLocals().add(conditionLocal);

        List<Unit> tobeInserted = new ArrayList<>();

        SootFieldRef isShadowFieldRef = Scene.v().makeFieldRef(
                sootClass,
                SHADOW_FIELD,
                BooleanType.v(),
                false  // non-static field
        );

        tobeInserted.add(Jimple.v().newAssignStmt(
                conditionLocal,
                Jimple.v().newInstanceFieldRef(
                        body.getThisLocal(),
                        isShadowFieldRef
                )
        ));


        // 创建if语句，使用EqExpr进行比较
        NopStmt endIf = Jimple.v().newNopStmt();

        // if (!isShadow) goto endIf
        tobeInserted.add(
                Jimple.v().newIfStmt(
                        Jimple.v().newEqExpr(
                                conditionLocal,
                                IntConstant.v(0)  // false corresponds to 0
                        ),
                        endIf
                )
        );

        // createFastForwardBaggage调用
        tobeInserted.add(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass(UTIL_CLASS_NAME),
                                        "createFastForwardBaggage",
                                        Collections.emptyList(),
                                        RefType.v("io.opentelemetry.api.baggage.Baggage"),
                                        true
                                ),
                                Collections.emptyList()
                        )
                )
        );

        tobeInserted.add(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newVirtualInvokeExpr(
                                body.getThisLocal(),
                                Scene.v().makeMethodRef(
                                        sootClass,
                                        "run$shadow",
                                        Collections.emptyList(),
                                        VoidType.v(),
                                        false
                                )
                        )
                )
        );

        // Add return void statement
        tobeInserted.add(Jimple.v().newReturnVoidStmt());

        tobeInserted.add(endIf);

        units.insertAfter(tobeInserted, lastIdentityStmt);
    }

    public List<Local> getLocals(Body body) {
        List<Local> res = new ArrayList<>();

        Set<Local> parameterLocals = getParameterLocals(body.getMethod());

        for (Local local : body.getLocals()) {
            if (parameterLocals.contains(local)) {
                continue;
            }
            res.add(local);
        }
        return res;
    }

    public void hookMicroFork(){
        lockAnalyzer = new LockAnalyzer(originalClass);
        lockAnalyzer.analyze();
        LOG.info("LockAnalyzer.analyze finished");
        //print item in lockAnalyzer.methodUnitIds
        for (Map.Entry<SootMethod, Map<Unit, String>> entry : lockAnalyzer.methodUnitIds.entrySet()) {
            SootMethod method = entry.getKey();
            for (Map.Entry<Unit, String> unitEntry : entry.getValue().entrySet()) {
                LOG.info("method: " + method.getSignature() + " unit: " + unitEntry.getKey() + " id: " + unitEntry.getValue());
            }
        }

        int count = 0;


        for (Map.Entry<SootMethod, Map<Unit, String>> entry : lockAnalyzer.methodUnitIds.entrySet()) {
            SootMethod method = entry.getKey();
            Map<Unit, String> units = entry.getValue();

            LOG.info("method to be instrumented is: " + method.getSignature() + " has " + entry.getValue().size() + " units");
            fastForwardMethodGenerator.addFastForwardMethod(method, method.getDeclaringClass(), units);
        }

        for(SootMethod method:fastForwardMethodGenerator.fastForwardMethods){
            fastForwardMethodGenerator.methodToLocalsMap.put(method, getBodyLocals(method.retrieveActiveBody()));
        }

        for (Map.Entry<SootMethod, Map<Unit, String>> entry : lockAnalyzer.methodUnitIds.entrySet()) {
            SootMethod method = entry.getKey();
            initializeLocalsForMethod(method.retrieveActiveBody());
        }

        for (Map.Entry<SootMethod, Map<Unit, String>> entry : lockAnalyzer.methodUnitIds.entrySet()) {
            SootMethod method = entry.getKey();
//            if(!method.getName().contains("run")){
//                continue;
//            }
            //initializeLocalsForMethod(method.retrieveActiveBody());
            LOG.info("method to be instrumented is: " + method.getSignature() + " has " + entry.getValue().size() + " units");

            List<Local> originalLocals = getLocals(method.retrieveActiveBody());

            for (Map.Entry<Unit, String> unitEntry : entry.getValue().entrySet()) {
                instrumentTrackingCode(method, unitEntry.getKey(), unitEntry.getValue(), originalLocals);
            }
            count++;
//            if(count>1){
//                break;
//            }
        }


//        for(SootMethod method:fastForwardMethodGenerator.fastForwardMethods){
//            List<Unit> gotoUnits = new ArrayList<>();
//            Map<Unit, String> methodUnitIds = fastForwardMethodGenerator.methodUnitIds.get(method);
//            for (Unit unit: methodUnitIds.keySet()) {
//                Body body = method.retrieveActiveBody();
//                Chain<Unit> units = body.getUnits();
//                List<Unit> restoreCode = createRestoreCode(body, method, fastForwardMethodGenerator.methodToLocalsMap.get(method));
//                LOG.info("restoreCode size is: " + restoreCode.size() +"in method: " + method.getSignature());
//                List<Unit> fieldRestoreCode = createFieldRestoreCode(body, method);
//                LOG.info("fieldRestoreCode size is: " + fieldRestoreCode.size() +"in method: " + method.getSignature());
//                LocalGeneratorUtil lg = new LocalGeneratorUtil(body);
//                NopStmt skipRestoreLabel = Jimple.v().newNopStmt();
//                NopStmt gotoStmt = Jimple.v().newNopStmt();
//                List<Unit> tobeInserted = new ArrayList<>();
//                tobeInserted.add(gotoStmt);
//                Local isFastForward = lg.generateLocalWithId(BooleanType.v(), "$isFastForward");
//                tobeInserted.add(Jimple.v().newAssignStmt(
//                        isFastForward,
//                        Jimple.v().newStaticInvokeExpr(
//                                Scene.v().makeMethodRef(
//                                        Scene.v().getSootClass(UTIL_CLASS_NAME),
//                                        "isFastForward",
//                                        Collections.emptyList(),
//                                        BooleanType.v(),
//                                        true
//                                )
//                        )
//                ));
//                tobeInserted.add(Jimple.v().newIfStmt(
//                        Jimple.v().newEqExpr(
//                                isFastForward,
//                                IntConstant.v(0)  // false
//                        ),
//                        skipRestoreLabel
//                ));
//                tobeInserted.addAll(restoreCode);
//                tobeInserted.addAll(fieldRestoreCode);
//                tobeInserted.add(skipRestoreLabel);
//                gotoUnits.add(gotoStmt);
//
//                units.insertBefore(tobeInserted, unit);
//            }
//            methodToGotoUnits.put(method, gotoUnits);
//        }
//
//        for(SootMethod method:fastForwardMethodGenerator.fastForwardMethods){
//            List<Unit> gotoUnits = methodToGotoUnits.get(method);
//            Body body = method.retrieveActiveBody();
//
//            LocalGeneratorUtil lg = new LocalGeneratorUtil(body);
//            Chain<Unit> units = body.getUnits();
//            List<Unit> originalUnits = new ArrayList<>(body.getUnits());
//            Local isFastForward = lg.generateLocalWithId(BooleanType.v(), "$isFastForward");
//
//            List<Unit> tobeInserted = new ArrayList<>();
//            tobeInserted.add(Jimple.v().newAssignStmt(
//                    isFastForward,
//                    Jimple.v().newStaticInvokeExpr(
//                            Scene.v().makeMethodRef(
//                                    Scene.v().getSootClass(UTIL_CLASS_NAME),
//                                    "isFastForward",
//                                    Collections.emptyList(),
//                                    BooleanType.v(),
//                                    true
//                            )
//                    )
//            ));
//            Unit firstNonIdentityStmt = getFirstNonIdentityStmt(body);
//
//            tobeInserted.add(Jimple.v().newIfStmt(
//                    Jimple.v().newEqExpr(
//                            isFastForward,
//                            IntConstant.v(0)
//                    ),
//                    firstNonIdentityStmt
//            ));
//            Local gotoPosition = lg.generateLocalWithId(IntType.v(), "$gotoPosition");
//            tobeInserted.add(Jimple.v().newAssignStmt(
//                    gotoPosition,
//                    Jimple.v().newStaticInvokeExpr(
//                            Scene.v().makeMethodRef(
//                                    Scene.v().getSootClass(UTIL_CLASS_NAME),
//                                    "getExecutingUnit",
//                                    Collections.emptyList(),
//                                    IntType.v(),
//                                    true
//                            )
//                    )
//            ));
//
//            List<IntConstant> lookupValues = new ArrayList<>();
//            List<Unit> lookupTargets = new ArrayList<>();
//
//            for (int i = 0; i < gotoUnits.size(); i++) {
//                lookupValues.add(IntConstant.v(i));
//                lookupTargets.add(gotoUnits.get(i));
//            }
//
//            LookupSwitchStmt switchStmt = Jimple.v().newLookupSwitchStmt(
//                    gotoPosition,
//                    lookupValues,
//                    lookupTargets,
//                    gotoUnits.get(0)
//            );
//
//            tobeInserted.add(switchStmt);
//
//            Unit lastIdentitiyStmt = getLastIdentityStmt(body);
//            assert lastIdentitiyStmt != null;
//            units.insertAfter(tobeInserted, lastIdentitiyStmt);
//        }
//
//        for (SootMethod sootMethod : fastForwardMethodGenerator.fastForwardMethods){
//            initializeLocalsForMethod(sootMethod.retrieveActiveBody());
//        }
//
//        redirectToShadowRun(originalClass);
//        fastForwardMethodGenerator.replaceWithFastForward();
//        instrumentShadowVersionMethods();
//        resetBaggage();
    }

    public void resetBaggage(){
        Map<Unit, SootMethod> divergePoints =lockAnalyzer.divergePoints;
        for (Map.Entry<Unit, SootMethod> entry : divergePoints.entrySet()) {
            Unit unit = entry.getKey();
            SootMethod method = entry.getValue();
            SootMethod shadowMethod = getShadowMethod(method);
            Unit shadowUnit = fastForwardMethodGenerator.originalToCloneMap.get(unit);
            Body body = shadowMethod.retrieveActiveBody();
            Chain<Unit> units = body.getUnits();
            LocalGeneratorUtil lg = new LocalGeneratorUtil(body);
            List<Unit> tobeInserted = new ArrayList<>();

            NopStmt skipLabel = Jimple.v().newNopStmt();

            Local isFastForward = lg.generateLocalWithId(BooleanType.v(), "$isFastForward");
            tobeInserted.add(
                    Jimple.v().newAssignStmt(
                            isFastForward,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass(UTIL_CLASS_NAME),
                                            "isFastForward",
                                            Collections.emptyList(),
                                            BooleanType.v(),
                                            true
                                    )
                            )
                    )
            );

            tobeInserted.add(
                    Jimple.v().newIfStmt(
                            Jimple.v().newEqExpr(isFastForward, IntConstant.v(0)),
                            skipLabel
                    )
            );

            tobeInserted.add(
                    Jimple.v().newInvokeStmt(
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass(UTIL_CLASS_NAME),
                                            "clearBaggage",
                                            Collections.emptyList(),
                                            VoidType.v(),
                                            true
                                    )
                            )
                    )
            );

            tobeInserted.add(
                    Jimple.v().newInvokeStmt(
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass(UTIL_CLASS_NAME),
                                            "createDryRunBaggage",
                                            Collections.emptyList(),
                                            RefType.v("io.opentelemetry.api.baggage.Baggage"),
                                            true
                                    )
                            )
                    )
            );

            tobeInserted.add(
                    Jimple.v().newInvokeStmt(
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass(UTIL_CLASS_NAME),
                                            "createShadowBaggage",
                                            Collections.emptyList(),
                                            RefType.v("io.opentelemetry.api.baggage.Baggage"),
                                            true
                                    )
                            )
                    )
            );

            tobeInserted.add(skipLabel);

            units.insertBefore(tobeInserted, shadowUnit);

            body.validate();
        }
    }

    public void createShadowMethod(SootMethod originalMethod, SootClass targetClass) {
        String shadowMethodName = originalMethod.getName() + SHADOW;
        SootMethod shadowMethod = new SootMethod(shadowMethodName,
                originalMethod.getParameterTypes(),
                originalMethod.getReturnType(),
                originalMethod.getModifiers());

        Body newBody = (Body) originalMethod.getActiveBody().clone();

        shadowMethod.setActiveBody(newBody);

        if (!targetClass.declaresMethod(shadowMethodName, originalMethod.getParameterTypes(), originalMethod.getReturnType())) {
            targetClass.addMethod(shadowMethod);
        }
    }

    public SootMethod getShadowMethod(SootMethod originalMethod) {

        String shadowMethodName = originalMethod.getName() + SHADOW;

        try {
            // First check if the shadow method already exists
            return originalMethod.getDeclaringClass().getMethod(shadowMethodName,
                    originalMethod.getParameterTypes(),
                    originalMethod.getReturnType());

        } catch (RuntimeException e) {
            LOG.info("Shadow method not found: " + e.getMessage());
            throw e;
        }
    }

    public Set<Local> getParameterLocals(SootMethod method) {
        Body body = method.retrieveActiveBody();
        HashSet<Local> res = new HashSet<>();
        for(Unit u: body.getUnits()) {
            if(u instanceof IdentityStmt) {
                IdentityStmt identityStmt = (IdentityStmt) u;
                // 检查右操作数是否为参数引用
                if(identityStmt.getLeftOp() instanceof Local &&
                        identityStmt.getRightOp() instanceof ParameterRef) {
                    res.add((Local) identityStmt.getLeftOp());
                }

                if(identityStmt.getLeftOp() instanceof Local &&
                        identityStmt.getRightOp() instanceof ThisRef) {
                    res.add((Local) identityStmt.getLeftOp());
                }
            }
        }
        return res;
    }

    public List<Local> getBodyLocals(Body body){
        List<Local> locals = new ArrayList<>();
        Set<Local> parameterLocals = getParameterLocals(body.getMethod());
        for(Local local: body.getLocals()){
            if(!parameterLocals.contains(local)){
                locals.add(local);
            }
        }
        return locals;
    }

    private void initializeLocalsForMethod(Body body) {
        Chain<Unit> units = body.getUnits();
        Unit lastIdentityStmt = getLastIdentityStmt(body);
        List<Unit> tobeInserted = new ArrayList<>();
        Set<Local> parameterLocals = getParameterLocals(body.getMethod());

        for(Local local : body.getLocals()) {
            if (parameterLocals.contains(local)) {
                continue;
            }
            LOG.info("Initializing local: " + local.getName() + " in method: " + body.getMethod().getSignature() + " type: " + local.getType() + " isParameter: " + parameterLocals.contains(local));

            Type type = local.getType();

            if(type instanceof PrimType) {
                if(type instanceof IntType) {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            IntConstant.v(0)
                    ));
                } else if(type instanceof BooleanType) {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            IntConstant.v(0)
                    ));
                } else if(type instanceof FloatType) {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            FloatConstant.v(0.0f)
                    ));
                } else if(type instanceof DoubleType) {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            DoubleConstant.v(0.0d)
                    ));
                } else if(type instanceof LongType) {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            LongConstant.v(0L)
                    ));
                } else if(type instanceof ByteType) {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            IntConstant.v(0)
                    ));
                } else if(type instanceof ShortType) {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            IntConstant.v(0)
                    ));
                } else if(type instanceof CharType) {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            IntConstant.v(0)
                    ));
                }
            } else {
                tobeInserted.add(Jimple.v().newAssignStmt(
                        local,
                        NullConstant.v()
                ));
            }
        }

        assert lastIdentityStmt!=null;
        LOG.info("Tobeinserted size in initializeLocalsForOriginalThread is: " + tobeInserted.size());
        units.insertAfter(tobeInserted, lastIdentityStmt);
        body.validate();
        LOG.info("Initialized " + tobeInserted.size() + " locals in method: " + body.getMethod().getSignature());
    }


    private void instrumentTrackingCode(SootMethod method, Unit unit, String unitId, List<Local> originalLocals) {
        LOG.info("Instrumenting method: " + method.getSignature() + " unit: " + unit + " id: " + unitId);
        Body body = method.retrieveActiveBody();
        Chain<Unit> units = body.getUnits();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        List<Unit> trackingCode = new ArrayList<>();


        trackingCode.add(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(UTIL_CLASS_NAME),
                                "recordExecutingUnit",
                                Arrays.asList(
                                        RefType.v("java.lang.String"),
                                        RefType.v("java.lang.String")
                                ),
                                VoidType.v(),
                                true
                        ),
                        Arrays.asList(
                                StringConstant.v(method.getSignature()),
                                StringConstant.v(unitId)
                        )
                )
        ));


        List<Unit> stateCode = createStateRecordCode(body, originalLocals);
        List<Unit> fieldCode = createFieldRecordCode(body, originalLocals);
        trackingCode.addAll(stateCode);
        trackingCode.addAll(fieldCode);

        // 统一插入所有代码
        try {
            units.insertBefore(trackingCode, unit);

            // 验证body
            body.validateLocals();
            body.validateTraps();
            body.validateUnitBoxes();
            body.validate();
        } catch (Exception e) {
            LOG.error("Failed to instrument tracking code: " + e.getMessage());
            throw e;
        }
    }

    public Set<Local> getExcludedLocals(Body body, Local stateMap){
        Set<Local> res = new HashSet<Local>();
        for(Unit unit:body.getUnits()){
            if(unit instanceof DefinitionStmt){
                DefinitionStmt defStmt = (DefinitionStmt) unit;
                if(defStmt.getLeftOp() instanceof Local && (((Local) defStmt.getLeftOp()).getName().equals(stateMap.getName()))){
                    res.add((Local) defStmt.getLeftOp());
                }
            }

            if(unit instanceof DefinitionStmt){
                DefinitionStmt defStmt = (DefinitionStmt) unit;
                if(defStmt.getLeftOp() instanceof Local && (defStmt.getRightOp() instanceof ThisRef)){
                    res.add((Local) defStmt.getLeftOp());
                }
            }
        }
        return res;
    }

    private List<Unit> createStateRecordCode(Body body, List<Local> originalLocals) {
        List<Unit> code = new ArrayList<>();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);


        Local stateMap = lg.generateLocalWithId(RefType.v("java.util.HashMap"), "$state");


        code.add(Jimple.v().newAssignStmt(
                stateMap,
                Jimple.v().newNewExpr(RefType.v("java.util.HashMap"))
        ));

        code.add(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        stateMap,
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass("java.util.HashMap"),
                                "<init>",
                                Collections.emptyList(),
                                VoidType.v(),
                                false
                        )
                )
        ));

        Set<Local> excludedLocals = getExcludedLocals(body, stateMap);

        int cnt =0;
        for(Local local : originalLocals) {


            Type type = local.getType();

            if(excludedLocals.contains(local)){
                continue;
            }

//            if(cnt++>19){
//                continue;
//            }
            Local wrappedValue = null;

            if(type instanceof PrimType){
                if(type instanceof IntType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Integer"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Integer"),
                                            "valueOf",
                                            Arrays.asList(IntType.v()),
                                            RefType.v("java.lang.Integer"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if (type instanceof BooleanType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Boolean"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Boolean"),
                                            "valueOf",
                                            Arrays.asList(BooleanType.v()),
                                            RefType.v("java.lang.Boolean"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if (type instanceof FloatType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Float"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Float"),
                                            "valueOf",
                                            Arrays.asList(FloatType.v()),
                                            RefType.v("java.lang.Float"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof DoubleType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Double"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Double"),
                                            "valueOf",
                                            Arrays.asList(DoubleType.v()),
                                            RefType.v("java.lang.Double"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof LongType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Long"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Long"),
                                            "valueOf",
                                            Arrays.asList(LongType.v()),
                                            RefType.v("java.lang.Long"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof ByteType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Byte"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Byte"),
                                            "valueOf",
                                            Arrays.asList(ByteType.v()),
                                            RefType.v("java.lang.Byte"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof ShortType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Short"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Short"),
                                            "valueOf",
                                            Arrays.asList(ShortType.v()),
                                            RefType.v("java.lang.Short"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof CharType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Short"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Short"),
                                            "valueOf",
                                            Arrays.asList(ShortType.v()),
                                            RefType.v("java.lang.Short"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                }
            }else {
                wrappedValue = local;
            }

            Local wrapContextLocal = lg.generateLocalWithId(RefType.v(WRAP_CONTEXT_CLASS_NAME), "$wrapContext");

            code.add(Jimple.v().newAssignStmt(
                    wrapContextLocal,
                    Jimple.v().newNewExpr(RefType.v(WRAP_CONTEXT_CLASS_NAME))
            ));

            code.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(
                            wrapContextLocal,
                            Scene.v().makeMethodRef(
                                    Scene.v().getSootClass(WRAP_CONTEXT_CLASS_NAME),
                                    "<init>",
                                    Arrays.asList(RefType.v("java.lang.Object")),  // 构造函数参数类型
                                    VoidType.v(),
                                    false
                            ),
                            Arrays.asList(wrappedValue)  // 传入wrappedValue作为构造函数参数
                    )
            ));


            code.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            stateMap,
                            Scene.v().makeMethodRef(
                                    Scene.v().getSootClass("java.util.HashMap"),
                                    "put",
                                    Arrays.asList(
                                            RefType.v("java.lang.Object"),
                                            RefType.v("java.lang.Object")
                                    ),
                                    RefType.v("java.lang.Object"),
                                    false
                            ),
                            Arrays.asList(
                                    StringConstant.v(local.getName()),
                                    wrapContextLocal
                            )
                    )
            ));
        }

        LOG.info("TraceUtil.recordState is called");
        List<Unit> printLogsBefore = printLog("Recording state for method : " + body.getMethod().getSignature(),lg);
        code.addAll(printLogsBefore);
        code.add(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(UTIL_CLASS_NAME),
                                "recordState",
                                Arrays.asList(
                                        RefType.v("java.lang.String"),
                                        RefType.v("java.util.HashMap")
                                ),
                                VoidType.v(),
                                true
                        ),
                        Arrays.asList(
                                StringConstant.v(body.getMethod().getSignature()),
                                stateMap
                        )
                )
        ));
        List<Unit> printLogsAfter = printLog("Recorded state for method: " + body.getMethod().getSignature(),lg);
        code.addAll(printLogsAfter);

        body.validate();

        return code;
    }

    private List<Unit> createFieldRecordCode(Body body, List<Local> originalLocals) {
        List<Unit> code = new ArrayList<>();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        // 创建存储field的HashMap
        Local stateMap = lg.generateLocalWithId(RefType.v("java.util.HashMap"), "$fieldState");
        code.add(Jimple.v().newAssignStmt(
                stateMap,
                Jimple.v().newNewExpr(RefType.v("java.util.HashMap"))
        ));

        // 初始化HashMap
        code.add(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        stateMap,
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass("java.util.HashMap"),
                                "<init>",
                                Collections.emptyList(),
                                VoidType.v(),
                                false
                        )
                )
        ));


        Local thisRef = null;
        boolean isStatic = body.getMethod().isStatic();

        if(!isStatic) {
            thisRef = body.getThisLocal();
        }


        Chain<SootField> fields = body.getMethod().getDeclaringClass().getFields();

        for(SootField field : fields) {
            Type type = field.getType();
            Local wrappedValue = null;

            if(field.getName().equals("this")){
                continue;
            }

            Local fieldValue = lg.generateLocalWithId(type, "$fieldValue");


            if(field.isStatic()) {
                // 静态字段使用staticFieldRef
                code.add(Jimple.v().newAssignStmt(
                        fieldValue,
                        Jimple.v().newStaticFieldRef(field.makeRef())
                ));
            } else {
                // 实例字段使用instanceFieldRef
                if(thisRef == null){
                    continue;
                }
                code.add(Jimple.v().newAssignStmt(
                        fieldValue,
                        Jimple.v().newInstanceFieldRef(
                                thisRef,
                                field.makeRef()
                        )
                ));
            }

            if(type instanceof PrimType) {
                // 基本类型需要装箱
                if(type instanceof IntType) {

                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Integer"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Integer"),
                                            "valueOf",
                                            Arrays.asList(IntType.v()),
                                            RefType.v("java.lang.Integer"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof BooleanType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Boolean"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Boolean"),
                                            "valueOf",
                                            Arrays.asList(BooleanType.v()),
                                            RefType.v("java.lang.Boolean"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof FloatType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Float"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Float"),
                                            "valueOf",
                                            Arrays.asList(FloatType.v()),
                                            RefType.v("java.lang.Float"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof DoubleType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Double"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Double"),
                                            "valueOf",
                                            Arrays.asList(DoubleType.v()),
                                            RefType.v("java.lang.Double"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof LongType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Long"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Long"),
                                            "valueOf",
                                            Arrays.asList(LongType.v()),
                                            RefType.v("java.lang.Long"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof ByteType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Byte"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Byte"),
                                            "valueOf",
                                            Arrays.asList(LongType.v()),
                                            RefType.v("java.lang.Byte"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                }

            } else {
                // 引用类型不需要装箱
                wrappedValue = fieldValue;
            }

            // 创建WrapContext并初始化
            Local wrapContextLocal = lg.generateLocalWithId(RefType.v(WRAP_CONTEXT_CLASS_NAME), "$wrapContext");
            code.add(Jimple.v().newAssignStmt(
                    wrapContextLocal,
                    Jimple.v().newNewExpr(RefType.v(WRAP_CONTEXT_CLASS_NAME))
            ));

            code.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(
                            wrapContextLocal,
                            Scene.v().makeMethodRef(
                                    Scene.v().getSootClass(WRAP_CONTEXT_CLASS_NAME),
                                    "<init>",
                                    Arrays.asList(RefType.v("java.lang.Object")),
                                    VoidType.v(),
                                    false
                            ),
                            Arrays.asList(wrappedValue)
                    )
            ));

            // 存入HashMap
            code.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            stateMap,
                            Scene.v().makeMethodRef(
                                    Scene.v().getSootClass("java.util.HashMap"),
                                    "put",
                                    Arrays.asList(RefType.v("java.lang.Object"), RefType.v("java.lang.Object")),
                                    RefType.v("java.lang.Object"),
                                    false
                            ),
                            Arrays.asList(
                                    StringConstant.v(field.getName()),
                                    wrapContextLocal
                            )
                    )
            ));
        }

        // 调用TraceUtil记录状态
        code.add(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(UTIL_CLASS_NAME),
                                "recordFieldState",  // 新方法名,区分field状态记录
                                Arrays.asList(
                                        RefType.v("java.lang.String"),
                                        RefType.v("java.util.HashMap")
                                ),
                                VoidType.v(),
                                true
                        ),
                        Arrays.asList(
                                StringConstant.v(body.getMethod().getSignature()),
                                stateMap
                        )
                )
        ));

        return code;
    }

}


