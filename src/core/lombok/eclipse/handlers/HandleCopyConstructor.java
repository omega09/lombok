/*
 * Copyright (C) 2009-2016 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.eclipse.handlers;

import static lombok.eclipse.Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.mangosdk.spi.ProviderFor;

import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.eclipse.handlers.EclipseHandlerUtil.FieldAccess;
import lombok.experimental.CopyConstructor;
import lombok.experimental.CopyConstructor.Depth;

/**
 * Handles the {@code lombok.CopyConstructor} annotation for eclipse.
 */

@ProviderFor(EclipseAnnotationHandler.class)
public class HandleCopyConstructor extends EclipseAnnotationHandler<CopyConstructor> {
	
	public final String name = "@CopyConstructor";
	
	public static boolean checkFieldAnnotationValidity(EclipseNode annotatedField) {
		short num = 0;
		if (hasAnnotation(CopyConstructor.Reference.class, annotatedField))
			num++;
		if (hasAnnotation(CopyConstructor.Copy.class, annotatedField))
			num++;
		if (hasAnnotation(CopyConstructor.Exclude.class, annotatedField))
			num++;
		return num <=1;
	}
	
	public void handle(AnnotationValues<CopyConstructor> annotation, Annotation ast, EclipseNode annotationNode) {
//		handleFlagUsage(annotationNode, ConfigurationKeys.FXPROPERTY_FLAG_USAGE, name);
		
		EclipseNode typeNode = annotationNode.up();
		if (typeNode == null)
			return;
		if (typeNode.getKind() != Kind.TYPE) {
			annotationNode.addError(name + " is only supported on a class.");
			return;
		}
		if (typeQualifiesForCopyConstructor(typeNode, annotationNode)) {
			Map<EclipseNode, Depth> fields = collectFields(annotation, typeNode);
			generateConstructor(typeNode, fields);
		}
	}
	
	public boolean typeQualifiesForCopyConstructor(EclipseNode typeNode, EclipseNode annotationNode) {
		TypeDeclaration typeDecl = (TypeDeclaration) typeNode.get();
		int modifiers = typeDecl == null ? 0 : typeDecl.modifiers;
		boolean notAClass = (modifiers &
				(ClassFileConstants.AccInterface | ClassFileConstants.AccAnnotation | ClassFileConstants.AccEnum)) != 0;
		if (typeDecl == null || notAClass) {
			annotationNode.addError(name + " is only supported on a class.");
			return false;
		}
		return true;
	}
	
	private Map<EclipseNode, Depth> collectFields(AnnotationValues<CopyConstructor> annotation, EclipseNode typeNode) {
		Map<EclipseNode, Depth> fields = new HashMap<EclipseNode, Depth>();
		for (EclipseNode child : typeNode.down()) {
			if (child.getKind() != Kind.FIELD)
				continue;
			FieldDeclaration fieldDecl = (FieldDeclaration) child.get();
			if (!filterField(fieldDecl))
				continue;
			if (hasAnnotation(CopyConstructor.Exclude.class, child))
				continue;
			if (hasAnnotation(CopyConstructor.Copy.class, child))
				fields.put(child, Depth.ONE);
			else if (hasAnnotation(CopyConstructor.Reference.class, child))
				fields.put(child, Depth.REFERENCE);
			else
				fields.put(child, annotation.getInstance().depth());
		}
		return fields;
	}
	
	public void generateConstructor(EclipseNode typeNode, Map<EclipseNode, Depth> fields) {
		ASTNode source = typeNode.get();
		TypeDeclaration typeDeclaration = (TypeDeclaration) source;
		long p = (long) source.sourceStart << 32 | source.sourceEnd;
		ConstructorDeclaration constructor = new ConstructorDeclaration(((CompilationUnitDeclaration) typeNode.top().get()).compilationResult);
	
		// constructor declaration: 'public Type(...)'
		constructor.modifiers = ClassFileConstants.AccPublic;
		constructor.selector = typeDeclaration.name;
		constructor.constructorCall = new ExplicitConstructorCall(ExplicitConstructorCall.ImplicitSuper);
		constructor.constructorCall.sourceStart = source.sourceStart;
		constructor.constructorCall.sourceEnd = source.sourceEnd;
		constructor.thrownExceptions = null;
		constructor.typeParameters = null;
		constructor.bits |= ECLIPSE_DO_NOT_TOUCH_FLAG;
		constructor.bodyStart = constructor.declarationSourceStart = constructor.sourceStart = source.sourceStart;
		constructor.bodyEnd = constructor.declarationSourceEnd = constructor.sourceEnd = source.sourceEnd;
		
		// constructor argument: 'final Type type'
		TypeReference typeReference = namePlusTypeParamsToTypeReference(typeDeclaration.name, typeDeclaration.typeParameters, p);
		char[] name = typeDeclaration.name;
		char[] argName = new char[name.length];
		System.arraycopy(name, 0, argName, 0, name.length);
		argName[0] = Character.toLowerCase(argName[0]);
		Argument arg = new Argument(argName, p, typeReference, Modifier.FINAL);
		constructor.arguments = new Argument[] { arg };

		// constructor body
		List<Statement> statements = new ArrayList<Statement>(fields.size());
		for (Entry<EclipseNode, Depth> fieldData : fields.entrySet()) {
			EclipseNode field = fieldData.getKey();
			if (fieldData.getValue() == Depth.REFERENCE) {
				Expression thisX = createFieldAccessor(field, FieldAccess.ALWAYS_FIELD, source);
				
				MessageSend getX = new MessageSend();
				getX.sourceStart = source.sourceStart; getX.sourceEnd = source.sourceEnd;
				setGeneratedBy(getX, source);
				getX.receiver = new SingleNameReference(argName, p);
				setGeneratedBy(getX.receiver, source);
				FieldDeclaration fieldDecl = (FieldDeclaration) field.get();
				TypeReference fieldType = copyType(fieldDecl.type, source);
				getX.selector = toGetterName(field, isBoolean(fieldType)).toCharArray();
				
				// reference assignment: 'this.X = type.getX()'
				Assignment assignment = new Assignment(thisX, getX, (int)p);
				assignment.sourceStart = source.sourceStart; assignment.sourceEnd = assignment.statementEnd = source.sourceEnd;
				statements.add(assignment);
			}
			else if (fieldData.getValue() == Depth.ONE) {
			
			}
		}
		
		constructor.statements = statements.isEmpty() ? null : statements.toArray(new Statement[statements.size()]);
		injectMethod(typeNode, constructor);
	}
}