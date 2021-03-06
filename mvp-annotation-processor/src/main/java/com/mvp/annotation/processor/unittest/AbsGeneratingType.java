package com.mvp.annotation.processor.unittest;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;

import javax.annotation.processing.Filer;

/**
 * Created by Andy on 14.12.2016.
 */

public abstract class AbsGeneratingType implements GeneratingClass {

    private Filer filer;
    private String packageName;

    public AbsGeneratingType(Filer filer, String packageName){
        this.filer = filer;
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }

    @Override
    public void generate(){
        writeClass(build());
    }

    protected abstract TypeSpec.Builder build();

    protected String concatSimpleNameWithPackage(String simpleClassName){
        if (packageName.equals(""))
            return simpleClassName;
        else
            return packageName + "." + simpleClassName;
    }

    protected void writeClass(TypeSpec.Builder builder) {
        try {
            JavaFile.builder(packageName, builder.build())
                    .addFileComment("Generated code")
                    .indent("   ")
                    .build().writeTo(filer);
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}
