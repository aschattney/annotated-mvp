package com.mvp.annotation.processor.unittest;

import com.mvp.annotation.InjectUIView;
import com.mvp.annotation.processor.Gang;
import com.squareup.javapoet.ClassName;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.mvp.annotation.processor.AnnotationUtils.getAnnotationValue;

/**
 * Created by Andy on 14.12.2016.
 */

public class UnitTestAnnotationProcessor extends AbstractProcessor {

    private static final String MEMBER_PRESENTER_CLASS = "presenter";
    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        this.processingEnv = env;
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {

        Set<? extends Element> viewElements = roundEnvironment.getElementsAnnotatedWith(InjectUIView.class);

        String packageName = getShortestPackageName(viewElements);

        if (packageName == null)
            return true;

        for (Element viewElement : viewElements) {
            if (viewElement.getKind() == ElementKind.FIELD){
                VariableElement variableElement = (VariableElement) viewElement;
                DeclaredType declaredType = (DeclaredType) variableElement.asType();
                TypeMirror activityType = declaredType.getTypeArguments().get(0);
                ClassName activityClass = ClassName.bestGuess(activityType.toString());
                Object value = getAnnotationValue(typeUtils.asElement(activityType), MEMBER_PRESENTER_CLASS).getValue();
                String presenterClassString = value.toString().replace(".class", "");
                ClassName presenterClassName = ClassName.bestGuess(presenterClassString);
                TypeElement presenterElement = elementUtils.getTypeElement(presenterClassString);
                DeclaredType presenterType = typeUtils.getDeclaredType(elementUtils.getTypeElement("com.mvp.MvpPresenter"));
                TypeMirror uiViewType = findViewTypeOfPresenter(presenterType, presenterElement.asType());
                ClassName uiViewClassName = ClassName.bestGuess(uiViewType.toString());
                Gang gang = new Gang(activityClass, presenterClassName, uiViewClassName);
                new TestControllerClass(filer, getPackageName(viewElement), gang).generate();
                new PresenterBuilderClass(filer, getPackageName(viewElement), gang).generate();
            }
        }

        new TestingContextClass(filer, packageName).generate();

        return true;
    }

    private String getPackageName(Element viewElement) {
        return elementUtils.getPackageOf(viewElement).getQualifiedName().toString();
    }

    private String getShortestPackageName(Set<? extends Element> viewElements) {
        String shortest = null;
        for (Element viewElement : viewElements) {
            String s = elementUtils.getPackageOf(viewElement).getQualifiedName().toString();
            if (shortest == null)
                shortest = s;
            else if (s.length() < shortest.length())
                shortest = s;
        }
        return shortest;
    }

    private TypeMirror findViewTypeOfPresenter(DeclaredType presenterType, TypeMirror currentPresenterType) {
        TypeMirror baseViewType = elementUtils.getTypeElement("com.mvp.MvpView").asType();
        TypeMirror viewType = null;
        List<? extends TypeMirror> typeMirrors = typeUtils.directSupertypes(currentPresenterType);
        for (TypeMirror typeMirror : typeMirrors) {
            TypeMirror erasure = typeUtils.erasure(typeMirror);
            if (typeUtils.isAssignable(erasure, presenterType.asElement().asType())) {
                DeclaredType declaredType = (DeclaredType) typeMirror;
                List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
                if (!typeArguments.isEmpty()) {
                    boolean found = false;
                    for (TypeMirror possibleViewType : typeArguments) {
                        if (typeUtils.isAssignable(possibleViewType, baseViewType) && possibleViewType.toString().contains(".")) {
                            viewType = possibleViewType;
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
            } else {
                viewType = findViewTypeOfPresenter(presenterType, typeMirror);
                if (viewType != null)
                    break;
            }
        }
        return viewType;
    }

    private ClassName toClassName(String s) {
        return ClassName.bestGuess(s);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supportedAnnotations = new HashSet<>();
        supportedAnnotations.add(InjectUIView.class.getCanonicalName());
        return supportedAnnotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
