#version 150 core

#ifndef FML_VARYING_TEX
#define FML_VARYING_TEX
#endif

#ifndef FML_VARYING_COLOR
#define FML_VARYING_COLOR
#endif

#ifndef FML_SAMPLER_TEX
#define FML_SAMPLER_TEX
#endif

out vec4 fragColor;

FML_VARYING_TEX in vec2 fTex;
FML_VARYING_COLOR in vec4 fColour;

FML_SAMPLER_TEX uniform sampler2D tex;

void main() {
    fragColor = fColour;
}
