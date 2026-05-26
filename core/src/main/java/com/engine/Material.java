package com.engine;

public class Material {

    //TODO color vectors

    private static final float DEFAULT_AMBIENT_STRENGTH = 0.2f;
    private static final float DEFAULT_DIFFUSE_STRENGTH = 1f;
    private static final float DEFAULT_SPECULAR_STRENGTH = 0.5f;
    private static final int DEFAULT_SHININESS = 32;

    float ambientStrength;
    float diffuseStrength;
    float specularStrength;
    //specular exponent
    int shininess;

    Material(float as, float ds, float ss, int shine) {
        ambientStrength = as;
        diffuseStrength = ds;
        specularStrength = ss;
        shininess = shine;
    }
    Material() {
        ambientStrength = DEFAULT_AMBIENT_STRENGTH;
        diffuseStrength = DEFAULT_DIFFUSE_STRENGTH;
        specularStrength = DEFAULT_SPECULAR_STRENGTH;
        shininess = DEFAULT_SHININESS;
    }
}
