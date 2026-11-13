// ==========================================================
// FILE: src/main/resources/shaders/phong.vert (Vertex Shader)
// Pass 2 (Lighting Pass): transformasi vertex ke clip space kamera
// dan ke Light Space untuk keperluan Shadow Mapping.
// ==========================================================
#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aTexCoords;

out vec3 FragPos;
out vec3 Normal;
out vec2 TexCoords;
out vec4 FragPosLightSpace;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform mat3 normalMatrix;      // transpose(inverse(mat3(model))) dihitung di CPU (JOML)
uniform mat4 lightSpaceMatrix;  // P_light * V_light

void main() {
    vec4 worldPos = model * vec4(aPos, 1.0);
    FragPos = worldPos.xyz;
    Normal = normalMatrix * aNormal;
    TexCoords = aTexCoords;
    FragPosLightSpace = lightSpaceMatrix * worldPos;
    gl_Position = projection * view * worldPos;
}
