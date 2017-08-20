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

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.mangosdk.spi.ProviderFor;

import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.experimental.CopyConstructor;

/**
 * Handles the {@code lombok.CopyConstructor.Exclude} annotation for eclipse.
 */

// priority
@ProviderFor(EclipseAnnotationHandler.class)
public class HandleCopyConstructorExclude extends EclipseAnnotationHandler<CopyConstructor.Exclude> {
	
	public final String name = "@CopyConstructor.Exclude";
	
	@Override
	public void handle(AnnotationValues<CopyConstructor.Exclude> annotation, Annotation ast, EclipseNode annotationNode) {
		EclipseNode annotatedField = annotationNode.up();
		if (annotatedField == null)
			return;
		if (annotatedField.getKind() != Kind.FIELD) {
			annotationNode.addError(name + " is only supported on a field.");
			return;
		}
		if (!HandleCopyConstructor.checkFieldAnnotationValidity(annotatedField)) {
			annotationNode.addError("At most one of Exclude, Reference or Copy are allowd.");
			return;
		}
		FieldDeclaration fieldDecl = (FieldDeclaration) annotatedField.get();
		if (!filterField(fieldDecl)) {
			annotationNode.addError("static fields are invalid target for copying.");
		}
		EclipseNode classWithAnnotatedField = annotatedField.up();
		if (!hasAnnotation(CopyConstructor.class, classWithAnnotatedField)) {
			annotationNode.addWarning(name + " requires @CopyConstructor on the class for it to mean anything.");
		} else {
			boolean isFinal = (fieldDecl.modifiers & ClassFileConstants.AccFinal) != 0;
			boolean isNull = fieldDecl.initialization instanceof NullLiteral;
			if (isFinal && isNull) {
				annotationNode.addWarning("final field initialzied to null and excluded from constructor will remain null forever.");
			}
		}
	}
}