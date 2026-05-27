package com.engine;

import com.badlogic.gdx.graphics.Color;

public class Material {

    //TODO color vectors

    enum Type {
        EMERALD(
            new Color(0.0215f, 0.1745f, 0.0215f, 1f),   // ambient
            new Color(0.07568f, 0.61424f, 0.07568f, 1f), // diffuse
            new Color(0.633f, 0.727811f, 0.633f, 1f),    // specular
            76.8f                                          // shininess
        ),
        GOLD(
            new Color(0.24725f, 0.1995f, 0.0745f, 1f),
            new Color(0.75164f, 0.60648f, 0.22648f, 1f),
            new Color(0.628281f, 0.555802f, 0.366065f, 1f),
            51.2f
        ),

        BRONZE(
            new Color(0.2125f, 0.1275f, 0.054f, 1f),
            new Color(0.714f, 0.4284f, 0.18144f, 1f),
            new Color(0.393548f, 0.271906f, 0.166721f, 1f),
            0.2f * 128f
        );

        public final Color ambient, diffuse, specular;
        public final float shininess;

        Type(Color ambient, Color diffuse, Color specular, float shininess) {
            this.ambient = ambient;
            this.diffuse = diffuse;
            this.specular = specular;
            this.shininess = shininess;
        }
    }

    private static final Color DEFAULT_DIFFUSE = new Color(1, 1, 1, 1);
    private static final Color DEFAULT_AMBIENT = DEFAULT_DIFFUSE.cpy().mul(0.2f);
    private static final Color DEFAULT_SPECULAR = DEFAULT_DIFFUSE.cpy().mul(0.5f);
    private static final int DEFAULT_SHININESS = 32;

    Color ambient;
    Color diffuse;
    Color specular;
    //specular exponent
    float shininess;

    Material(Color a, Color d, Color s, float shine) {
        ambient = a;
        diffuse = d;
        specular = s;
        shininess = shine;
    }

    Material() {
        ambient = DEFAULT_AMBIENT.cpy();
        diffuse = DEFAULT_DIFFUSE.cpy();
        specular = DEFAULT_SPECULAR.cpy();
        shininess = DEFAULT_SHININESS;
    }

    Material(Type type) {
        this.ambient = type.ambient.cpy();
        this.diffuse = type.diffuse.cpy();
        this.specular = type.specular.cpy();
        this.shininess = type.shininess;
    }

    public void set(Material copy) {
        ambient.set(copy.ambient);
        diffuse.set(copy.diffuse);
        specular.set(copy.specular);
        shininess = copy.shininess;
    }

    public Material clone() {
        Material copy = new Material();
        copy.ambient.set(ambient);
        copy.diffuse.set(diffuse);
        copy.specular.set(specular);
        copy.shininess = shininess;
        return copy;
    }
}
