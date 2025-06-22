#version 150 core

// these may be defined by the apiRenderer to `layout(location = X)` during shader compilation
#ifndef FML_ATTRIB_POSITION
#define FML_ATTRIB_POSITION
#endif

#ifndef FML_ATTRIB_UV
#define FML_ATTRIB_UV
#endif

#ifndef FML_ATTRIB_COLOR
#define FML_ATTRIB_COLOR
#endif

#ifndef FML_VARYING_TEX
#define FML_VARYING_TEX
#endif

#ifndef FML_VARYING_COLOR
#define FML_VARYING_COLOR
#endif

//#ifndef FML_SCREEN_SIZE
//#define FML_SCREEN_SIZE vec2(854, 480)
//#endif

const vec2 screenSize = FML_SCREEN_SIZE;

FML_ATTRIB_POSITION in vec2 position;
FML_ATTRIB_UV in vec2 uv;
FML_ATTRIB_COLOR in vec4 color;
FML_VARYING_TEX out vec2 fTex;
FML_VARYING_COLOR out vec4 fColour;

void main() {
    fTex = uv;
    fColour = color;
    gl_Position = vec4((position / screenSize) * 2 - 1, 0.0, 1.0);
}
