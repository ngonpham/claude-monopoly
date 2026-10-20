package vn.cotyphu.gl

import android.opengl.GLES30
import android.util.Log

// GLSL ES 3.00. Mô phỏng MeshStandardMaterial của Three.js ở mức đủ giống:
// ánh sáng bán cầu + mặt trời có bóng PCF, specular Blinn-Phong theo roughness/metalness,
// tone mapping ACES Filmic (bản của Three.js) rồi mã hoá sRGB, sương mù tuyến tính.

object Shaders {
    const val MAIN_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
uniform mat4 uModel;
uniform mat4 uNormalMat;
uniform mat4 uViewProj;
uniform mat4 uLightVP;
uniform vec2 uUvOffset;
out vec3 vWorldPos;
out vec3 vNormal;
out vec2 vUv;
out vec4 vLightPos;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vWorldPos = wp.xyz;
    vNormal = mat3(uNormalMat) * aNormal;
    // Bitmap tải lên có hàng 0 ở t = 0; Three.js lật ảnh (flipY) nên ảnh đúng chiều ở v = 1 → lật lại ở đây.
    vUv = vec2(aUv.x + uUvOffset.x, 1.0 - (aUv.y + uUvOffset.y));
    vLightPos = uLightVP * wp;
    gl_Position = uViewProj * wp;
}
"""

    const val MAIN_FS = """#version 300 es
precision highp float;
precision highp sampler2DShadow;
in vec3 vWorldPos;
in vec3 vNormal;
in vec2 vUv;
in vec4 vLightPos;
uniform vec3 uColor;
uniform float uOpacity;
uniform vec3 uEmissive;
uniform float uRoughness;
uniform float uMetalness;
uniform int uFlags;
uniform sampler2D uMap;
uniform sampler2D uEmissiveMap;
uniform sampler2DShadow uShadowMap;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uFillDir;
uniform vec3 uFillColor;
uniform vec3 uHemiSky;
uniform vec3 uHemiGround;
uniform vec3 uCameraPos;
uniform vec3 uFogColor;
uniform vec2 uFog;
uniform float uExposure;
out vec4 fragColor;

const float PI = 3.14159265;

float shadowAt() {
    vec3 p = vLightPos.xyz / vLightPos.w * 0.5 + 0.5;
    if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
    vec2 texel = 1.0 / vec2(textureSize(uShadowMap, 0));
    float s = 0.0;
    for (int x = -1; x <= 1; x++)
        for (int y = -1; y <= 1; y++)
            s += texture(uShadowMap, vec3(p.xy + vec2(x, y) * texel, p.z - 0.0012));
    return s / 9.0;
}

vec3 rrtOdt(vec3 v) {
    vec3 a = v * (v + 0.0245786) - 0.000090537;
    vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
    return a / b;
}

vec3 aces(vec3 c) {
    const mat3 inM = mat3(vec3(0.59719, 0.07600, 0.02840), vec3(0.35458, 0.90834, 0.13383), vec3(0.04823, 0.01566, 0.83777));
    const mat3 outM = mat3(vec3(1.60475, -0.10208, -0.00327), vec3(-0.53108, 1.10813, -0.07276), vec3(-0.07367, -0.00605, 1.07602));
    c *= uExposure / 0.6;
    c = inM * c;
    c = rrtOdt(c);
    c = outM * c;
    return clamp(c, 0.0, 1.0);
}

vec3 toSrgb(vec3 c) {
    return mix(c * 12.92, pow(c, vec3(1.0 / 2.4)) * 1.055 - 0.055, step(0.0031308, c));
}

void main() {
    bool flatShade = (uFlags & 1) != 0;
    bool hasMap = (uFlags & 2) != 0;
    bool hasEmissiveMap = (uFlags & 4) != 0;
    bool unlit = (uFlags & 8) != 0;
    bool receive = (uFlags & 16) != 0;
    bool doubleSide = (uFlags & 32) != 0;

    vec3 albedo = uColor;
    float alpha = uOpacity;
    if (hasMap) {
        vec4 t = texture(uMap, vUv);
        albedo *= t.rgb;
        alpha *= t.a;
    }
    vec3 col;
    if (unlit) {
        col = albedo;
    } else {
        vec3 n = flatShade ? normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos))) : normalize(vNormal);
        if (!flatShade && doubleSide && !gl_FrontFacing) n = -n;
        vec3 v = normalize(uCameraPos - vWorldPos);
        float sh = receive ? shadowAt() : 1.0;
        float ndl = max(dot(n, uSunDir), 0.0);
        float ndlFill = max(dot(n, uFillDir), 0.0);
        vec3 hemi = mix(uHemiGround, uHemiSky, 0.5 * n.y + 0.5);
        vec3 diffColor = albedo * (1.0 - uMetalness);
        vec3 specColor = mix(vec3(0.04), albedo, uMetalness);
        float r = clamp(uRoughness, 0.06, 1.0);
        float shin = 2.0 / (r * r * r * r) - 2.0;
        vec3 h = normalize(uSunDir + v);
        float spec = pow(max(dot(n, h), 0.0), shin) * (shin + 8.0) / (8.0 * PI);
        vec3 direct = uSunColor * ndl * sh + uFillColor * ndlFill;
        col = diffColor * (hemi + direct) / PI;
        col += specColor * spec * uSunColor * ndl * sh;
        col += specColor * hemi * 0.25 * (1.0 - r);
    }
    vec3 e = uEmissive;
    if (hasEmissiveMap) e *= texture(uEmissiveMap, vUv).rgb;
    col += e;
    col = toSrgb(aces(col));
    float d = length(uCameraPos - vWorldPos);
    col = mix(col, uFogColor, smoothstep(uFog.x, uFog.y, d));
    fragColor = vec4(col, alpha);
}
"""

    const val DEPTH_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
uniform mat4 uModel;
uniform mat4 uLightVP;
void main() { gl_Position = uLightVP * uModel * vec4(aPos, 1.0); }
"""

    const val DEPTH_FS = """#version 300 es
precision mediump float;
void main() {}
"""

    // Trời: dải màu dọc giống canvas gradient của bản web (đỉnh xanh → chân trời vàng nhạt).
    const val SKY_VS = """#version 300 es
out float vY;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vY = p.y;
    gl_Position = vec4(p * 2.0 - 1.0, 1.0, 1.0);
}
"""

    const val SKY_FS = """#version 300 es
precision mediump float;
in float vY;
out vec4 fragColor;
void main() {
    float t = clamp(1.0 - vY, 0.0, 1.0);
    vec3 top = vec3(0.239, 0.498, 0.769);
    vec3 mid = vec3(0.663, 0.827, 0.925);
    vec3 low = vec3(0.965, 0.863, 0.706);
    vec3 c = t < 0.55 ? mix(top, mid, t / 0.55) : mix(mid, low, clamp((t - 0.55) / 0.2, 0.0, 1.0));
    fragColor = vec4(c, 1.0);
}
"""

    fun program(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(s)
                Log.e("CoTyPhuGL", "Lỗi biên dịch shader: $log")
                throw IllegalStateException("Shader compile failed: $log")
            }
            return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw IllegalStateException("Program link failed: ${GLES30.glGetProgramInfoLog(p)}")
        return p
    }
}
