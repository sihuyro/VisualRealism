// ==========================================================
// FILE: src/main/resources/shaders/phong.frag (Fragment Shader)
// Per-pixel lighting: Phong / Blinn-Phong + Texture + Shadow Mapping
// ==========================================================
#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 FragPosLightSpace;

uniform vec3 lightPos;
uniform vec3 viewPos;
uniform vec3 lightColor;
uniform vec3 objectColor;          // warna material (dipakai saat tekstur nonaktif)
uniform vec3 textureTint;          // pengali warna tekstur

uniform sampler2D textureSampler;   // texture unit 0
uniform sampler2D shadowMap;        // texture unit 1

uniform bool useTexture;
uniform bool useBlinnPhong;
uniform bool useShadows;
uniform bool usePCF;
uniform bool unlit;                 // penanda sumber cahaya (tanpa pencahayaan)

uniform int biasMode;               // 0 = tanpa bias, 1 = bias modul, 2 = slope-scaled tuned
uniform float ambientStrength;
uniform float specularStrength;
uniform float shininess;

// Mengembalikan 0.0 (terang) .. 1.0 (sepenuhnya dalam bayangan)
float shadowCalculation(vec4 fragPosLightSpace, vec3 N, vec3 L) {
    // Perspective divide -> NDC [-1,1] -> [0,1] (koordinat tekstur & depth)
    vec3 proj = fragPosLightSpace.xyz / fragPosLightSpace.w;
    proj = proj * 0.5 + 0.5;
    if (proj.z > 1.0) {
        return 0.0;  // di luar far plane frustum cahaya -> anggap terang
    }

    float cosTheta = clamp(dot(N, L), 0.0, 1.0);
    float bias = 0.0;
    if (biasMode == 1) {
        // Rumus pada modul: bias = max(0.05 * (1 - N.L), 0.005)
        bias = max(0.05 * (1.0 - cosTheta), 0.005);
    } else if (biasMode == 2) {
        // Slope-scaled depth bias: sebanding tan(theta), dibatasi agar tidak Peter Panning
        float tanTheta = sqrt(1.0 - cosTheta * cosTheta) / max(cosTheta, 1e-3);
        bias = clamp(0.002 * tanTheta, 0.0008, 0.01);
    }

    float dCurrent = proj.z;
    float shadow = 0.0;
    if (usePCF) {
        // Percentage-Closer Filtering 3x3 untuk melembutkan tepi bayangan
        vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                float dShadow = texture(shadowMap, proj.xy + vec2(x, y) * texel).r;
                shadow += (dCurrent - bias > dShadow) ? 1.0 : 0.0;
            }
        }
        shadow /= 9.0;
    } else {
        float dShadow = texture(shadowMap, proj.xy).r;
        shadow = (dCurrent - bias > dShadow) ? 1.0 : 0.0;
    }
    return shadow;
}

void main() {
    vec3 baseColor = useTexture ? texture(textureSampler, TexCoords).rgb * textureTint : objectColor;

    if (unlit) {
        FragColor = vec4(objectColor, 1.0);
        return;
    }

    vec3 N = normalize(Normal);
    if (!gl_FrontFacing) {
        N = -N;  // two-sided lighting saat Back-Face Culling dimatikan
    }
    vec3 L = normalize(lightPos - FragPos);
    vec3 V = normalize(viewPos - FragPos);

    // 1. Ambient: I_a * k_a
    vec3 ambient = ambientStrength * lightColor;

    // 2. Diffuse (Lambert): k_d * I_d * max(N.L, 0)
    float diff = max(dot(N, L), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Specular: Phong (R.V)^n  vs  Blinn-Phong (N.H)^n
    float spec = 0.0;
    if (diff > 0.0) {
        if (useBlinnPhong) {
            vec3 H = normalize(L + V);                      // Halfway vector
            spec = pow(max(dot(N, H), 0.0), shininess);
        } else {
            vec3 R = reflect(-L, N);                        // R = 2(N.L)N - L
            spec = pow(max(dot(V, R), 0.0), shininess);
        }
    }
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Shadow factor: terang = 1.0, dalam bayangan = 0.2 (sesuai modul)
    float shadow = useShadows ? shadowCalculation(FragPosLightSpace, N, L) : 0.0;
    float shadowFactor = 1.0 - 0.8 * shadow;

    vec3 result = (ambient + shadowFactor * diffuse) * baseColor + shadowFactor * specular;
    FragColor = vec4(result, 1.0);
}
