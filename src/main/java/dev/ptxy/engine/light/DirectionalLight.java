package dev.ptxy.engine.light;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

@Getter
@Setter
@AllArgsConstructor
public final class DirectionalLight {
    private Vector3f direction;
    private Vector3f color;
}
