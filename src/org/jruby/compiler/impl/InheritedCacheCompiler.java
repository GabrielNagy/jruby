/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.compiler.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jruby.Ruby;
import org.jruby.RubyFixnum;
import org.jruby.RubyRegexp;
import org.jruby.RubySymbol;
import org.jruby.ast.NodeType;
import org.jruby.compiler.ASTInspector;
import org.jruby.compiler.CacheCompiler;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.BlockBody;
import org.jruby.runtime.CallSite;
import org.jruby.runtime.CallType;
import org.jruby.runtime.CompiledBlockCallback;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;
import org.objectweb.asm.Opcodes;
import static org.jruby.util.CodegenUtils.*;

/**
 *
 * @author headius
 */
public class InheritedCacheCompiler implements CacheCompiler {
    protected StandardASMCompiler scriptCompiler;
    int callSiteCount = 0;
    List<String> callSiteList = new ArrayList<String>();
    List<CallType> callTypeList = new ArrayList<CallType>();
    Map<String, Integer> byteListIndices = new HashMap<String, Integer>();
    Map<String, ByteList> byteListValues = new HashMap<String, ByteList>();
    Map<BigInteger, String> bigIntegers = new HashMap<BigInteger, String>();
    Map<String, Integer> symbolIndices = new HashMap<String, Integer>();
    Map<Long, Integer> fixnumIndices = new HashMap<Long, Integer>();
    int inheritedSymbolCount = 0;
    int inheritedRegexpCount = 0;
    int inheritedFixnumCount = 0;
    int inheritedConstantCount = 0;
    int inheritedByteListCount = 0;
    int inheritedBlockBodyCount = 0;
    int inheritedBlockCallbackCount = 0;
    
    public InheritedCacheCompiler(StandardASMCompiler scriptCompiler) {
        this.scriptCompiler = scriptCompiler;
    }
    
    public void cacheCallSite(BaseBodyCompiler method, String name, CallType callType) {
        // retrieve call site from sites array
        method.loadThis();
        method.method.pushInt(callSiteCount);
        method.method.invokevirtual(scriptCompiler.getClassname(), "getCallSite", sig(CallSite.class, int.class));

        // add name to call site list
        callSiteList.add(name);
        callTypeList.add(callType);
        
        callSiteCount++;
    }
    
    public void cacheSymbol(BaseBodyCompiler method, String symbol) {
        Integer index = symbolIndices.get(symbol);
        if (index == null) {
            index = new Integer(inheritedSymbolCount++);
            symbolIndices.put(symbol, index);
        }

        method.loadThis();
        method.loadRuntime();
        method.method.ldc(index.intValue());
        method.method.ldc(symbol);
        method.method.invokevirtual(scriptCompiler.getClassname(), "getSymbol", sig(RubySymbol.class, Ruby.class, int.class, String.class));
    }

    public void cacheRegexp(BaseBodyCompiler method, String pattern, int options) {
        method.loadThis();
        method.loadRuntime();
        method.method.pushInt(inheritedRegexpCount++);
        method.method.ldc(pattern);
        method.method.ldc(options);
        method.method.invokevirtual(scriptCompiler.getClassname(), "getRegexp", sig(RubyRegexp.class, Ruby.class, int.class, String.class, int.class));
    }
    
    public void cacheFixnum(BaseBodyCompiler method, long value) {
        if (value <= 5 && value >= -1) {
            method.loadRuntime();
            switch ((int)value) {
            case -1:
                method.method.invokestatic(p(RubyFixnum.class), "minus_one", sig(RubyFixnum.class, Ruby.class));
                break;
            case 0:
                method.method.invokestatic(p(RubyFixnum.class), "zero", sig(RubyFixnum.class, Ruby.class));
                break;
            case 1:
                method.method.invokestatic(p(RubyFixnum.class), "one", sig(RubyFixnum.class, Ruby.class));
                break;
            case 2:
                method.method.invokestatic(p(RubyFixnum.class), "two", sig(RubyFixnum.class, Ruby.class));
                break;
            case 3:
                method.method.invokestatic(p(RubyFixnum.class), "three", sig(RubyFixnum.class, Ruby.class));
                break;
            case 4:
                method.method.invokestatic(p(RubyFixnum.class), "four", sig(RubyFixnum.class, Ruby.class));
                break;
            case 5:
                method.method.invokestatic(p(RubyFixnum.class), "five", sig(RubyFixnum.class, Ruby.class));
                break;
            default:
                throw new RuntimeException("wtf?");
            }
        } else {
            Integer index = fixnumIndices.get(value);
            if (index == null) {
                index = new Integer(inheritedFixnumCount++);
                fixnumIndices.put(value, index);
            }
            
            method.loadThis();
            method.loadRuntime();
            method.method.pushInt(index.intValue());
            if (value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE) {
                method.method.pushInt((int)value);
                method.method.invokevirtual(scriptCompiler.getClassname(), "getFixnum", sig(RubyFixnum.class, Ruby.class, int.class, int.class));
            } else {
                method.method.ldc(value);
                method.method.invokevirtual(scriptCompiler.getClassname(), "getFixnum", sig(RubyFixnum.class, Ruby.class, int.class, long.class));
            }
        }
    }

    public void finish() {
        SkinnyMethodAdapter initMethod = scriptCompiler.getInitMethod();

        // generate call sites initialization code
        int size = callSiteList.size();
        if (size != 0) {
            initMethod.aload(0);
            initMethod.pushInt(size);
            initMethod.anewarray(p(CallSite.class));
            
            for (int i = size - 1; i >= 0; i--) {
                String name = callSiteList.get(i);
                CallType callType = callTypeList.get(i);

                initMethod.pushInt(i);
                initMethod.ldc(name);
                if (callType.equals(CallType.NORMAL)) {
                    initMethod.invokestatic(scriptCompiler.getClassname(), "setCallSite", sig(CallSite[].class, params(CallSite[].class, int.class, String.class)));
                } else if (callType.equals(CallType.FUNCTIONAL)) {
                    initMethod.invokestatic(scriptCompiler.getClassname(), "setFunctionalCallSite", sig(CallSite[].class, params(CallSite[].class, int.class, String.class)));
                } else if (callType.equals(CallType.VARIABLE)) {
                    initMethod.invokestatic(scriptCompiler.getClassname(), "setVariableCallSite", sig(CallSite[].class, params(CallSite[].class, int.class, String.class)));
                }
            }
            initMethod.putfield(scriptCompiler.getClassname(), "callSites", ci(CallSite[].class));
        }

        // generate symbols initialization code
        size = inheritedSymbolCount;
        if (size != 0) {
            initMethod.aload(0);
            initMethod.pushInt(size);
            initMethod.invokevirtual(scriptCompiler.getClassname(), "initSymbols", sig(void.class, params(int.class)));
        }

        // generate fixnums initialization code
        size = inheritedFixnumCount;
        if (size != 0) {
            initMethod.aload(0);
            initMethod.pushInt(size);
            initMethod.invokevirtual(scriptCompiler.getClassname(), "initFixnums", sig(void.class, params(int.class)));
        }

        // generate constants initialization code
        size = inheritedConstantCount;
        if (size != 0) {
            initMethod.aload(0);
            initMethod.pushInt(size);
            initMethod.invokevirtual(scriptCompiler.getClassname(), "initConstants", sig(void.class, params(int.class)));
        }

        // generate regexps initialization code
        size = inheritedRegexpCount;
        if (size != 0) {
            initMethod.aload(0);
            initMethod.pushInt(size);
            initMethod.invokevirtual(scriptCompiler.getClassname(), "initRegexps", sig(void.class, params(int.class)));
        }

        // generate block bodies initialization code
        size = inheritedBlockBodyCount;
        if (size != 0) {
            initMethod.aload(0);
            initMethod.pushInt(size);
            initMethod.invokevirtual(scriptCompiler.getClassname(), "initBlockBodies", sig(void.class, params(int.class)));
        }

        // generate block bodies initialization code
        size = inheritedBlockCallbackCount;
        if (size != 0) {
            initMethod.aload(0);
            initMethod.pushInt(size);
            initMethod.invokevirtual(scriptCompiler.getClassname(), "initBlockCallbacks", sig(void.class, params(int.class)));
        }

        // generate bytelists initialization code
        size = inheritedByteListCount;
        if (inheritedByteListCount > 0) {
            // getter method to reduce bytecode at load point
            SkinnyMethodAdapter getter = new SkinnyMethodAdapter(
                    scriptCompiler.getClassVisitor().visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC, "getByteList", sig(ByteList.class, int.class), null, null));
            getter.start();
            getter.getstatic(scriptCompiler.getClassname(), "byteLists", ci(ByteList[].class));
            getter.iload(0);
            getter.aaload();
            getter.areturn();
            getter.end();
            // construction and population of the array in clinit
            SkinnyMethodAdapter clinitMethod = scriptCompiler.getClassInitMethod();
            scriptCompiler.getClassVisitor().visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC, "byteLists", ci(ByteList[].class), null, null);
            clinitMethod.ldc(size);
            clinitMethod.anewarray(p(ByteList.class));
            clinitMethod.putstatic(scriptCompiler.getClassname(), "byteLists", ci(ByteList[].class));

            for (Map.Entry<String, Integer> entry : byteListIndices.entrySet()) {
                int index = entry.getValue();
                ByteList byteList = byteListValues.get(entry.getKey());

                clinitMethod.getstatic(scriptCompiler.getClassname(), "byteLists", ci(ByteList[].class));
                clinitMethod.ldc(index);
                clinitMethod.ldc(byteList.toString());
                clinitMethod.invokestatic(p(ByteList.class), "create", sig(ByteList.class, CharSequence.class));
                clinitMethod.arraystore();
            }
        }
    }

    public void cacheConstant(BaseBodyCompiler method, String constantName) {
        method.loadThis();
        method.loadThreadContext();
        method.method.ldc(constantName);
        method.method.pushInt(inheritedConstantCount);
        method.method.invokevirtual(scriptCompiler.getClassname(), "getConstant", sig(IRubyObject.class, ThreadContext.class, String.class, int.class));

        inheritedConstantCount++;
    }

    public void cacheByteList(BaseBodyCompiler method, ByteList contents) {
        String asString = contents.toString();
        Integer index = byteListIndices.get(asString);
        if (index == null) {
            index = new Integer(inheritedByteListCount++);
            byteListIndices.put(asString, index);
            byteListValues.put(asString, contents);
        }

        method.method.ldc(index.intValue());
        method.method.invokestatic(scriptCompiler.getClassname(), "getByteList", sig(ByteList.class, int.class));
    }

    public void cacheBigInteger(BaseBodyCompiler method, BigInteger bigint) {
        String fieldName = bigIntegers.get(bigint);
        if (fieldName == null) {
            SkinnyMethodAdapter clinitMethod = scriptCompiler.getClassInitMethod();
            fieldName = scriptCompiler.getNewStaticConstant(ci(BigInteger.class), "bigInt");
            bigIntegers.put(bigint, fieldName);

            clinitMethod.newobj(p(BigInteger.class));
            clinitMethod.dup();
            clinitMethod.ldc(bigint.toString());
            clinitMethod.invokespecial(p(BigInteger.class), "<init>", sig(void.class, String.class));
            clinitMethod.putstatic(scriptCompiler.getClassname(), fieldName, ci(BigInteger.class));
        }

        method.method.getstatic(scriptCompiler.getClassname(), fieldName, ci(BigInteger.class));
    }

    public void cacheClosure(BaseBodyCompiler method, String closureMethod, int arity, StaticScope scope, boolean hasMultipleArgsHead, NodeType argsNodeId, ASTInspector inspector) {
        method.loadThis();
        method.loadThreadContext();
        method.method.pushInt(inheritedBlockBodyCount++);

        // build scope names string
        StringBuffer scopeNames = new StringBuffer();
        for (int i = 0; i < scope.getVariables().length; i++) {
            if (i != 0) scopeNames.append(';');
            scopeNames.append(scope.getVariables()[i]);
        }

        // build descriptor string
        String descriptor =
                closureMethod + ',' +
                arity + ',' +
                scopeNames + ',' +
                hasMultipleArgsHead + ',' +
                BlockBody.asArgumentType(argsNodeId) + ',' +
                !(inspector.hasClosure() || inspector.hasScopeAwareMethods());
        
        method.method.ldc(descriptor);
        method.method.invokevirtual(scriptCompiler.getClassname(), "getBlockBody", sig(BlockBody.class, ThreadContext.class, int.class, String.class));
    }

    public void cacheSpecialClosure(BaseBodyCompiler method, String closureMethod) {
        method.loadThis();
        method.loadRuntime();
        method.method.pushInt(inheritedBlockCallbackCount++);
        method.method.ldc(closureMethod);

        method.method.invokevirtual(scriptCompiler.getClassname(), "getBlockCallback", sig(CompiledBlockCallback.class, Ruby.class, int.class, String.class));
    }
}
