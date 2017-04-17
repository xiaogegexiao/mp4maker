#pragma version(1)
#pragma rs java_package_name(com.cammy.cammy)
#include "rs_graphics.rsh"
#include "rs_debug.rsh"

rs_allocation gYUVFrame;
int frameWidth, frameHeight;
int frameSize;

void __attribute__((kernel)) rgb2yuvFrames(uchar4 pixel, uint32_t x, uint32_t y) {
    uchar3 yuvChar;
    yuvChar.x = ((66 * pixel.x + 129 * pixel.y + 25 * pixel.z + 128) >> 8) + 16;
    yuvChar.y = ((-38 * pixel.x - 74 * pixel.y + 112 * pixel.z + 128) >> 8) + 128;
    yuvChar.z = ((112 * pixel.x - 94 * pixel.y - 18 * pixel.z + 128) >> 8) + 128;

    rsSetElementAt_char(gYUVFrame, yuvChar.x, (y * frameWidth) + x);
    if ((x%2 == 0) && (y%2 == 0)) {
        rsSetElementAt_char(gYUVFrame, yuvChar.y, frameSize + x + y*frameWidth/2);
        rsSetElementAt_char(gYUVFrame, yuvChar.z, frameSize + x + y*frameWidth/2 + 1);
    }
}