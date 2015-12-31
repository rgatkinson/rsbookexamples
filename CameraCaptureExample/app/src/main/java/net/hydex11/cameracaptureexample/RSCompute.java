package net.hydex11.cameracaptureexample;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Int2;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;

/**
 * Created by Alberto on 28/12/2015.
 */
public class RSCompute {

    RenderScript mRS;

    // Script vars
    ScriptIntrinsicYuvToRGB sYUV; // Used to convert input image from YUV to RGBA elements
    ScriptC_main scriptCMain; // Our custom script
    ScriptC_customYUVToRGBAConverter customYUVToRGBAConverter;

    // Boolean to use a custom YUV to RGB kernel, instead of the custom intrinsic script
    boolean useCustomYUVToRGBConversion = false;

    // Allocations
    Allocation inputAllocation; // Camera preview YUV allocation

    // Temporary intermediate allocation, used to store YUV to RGBA conversion output and
    // used as our custom script input
    Allocation midAllocation;

    Point mInputImageSize, mOutputAllocationSize;

    // Funcs
    public void compute(byte[] dataIn, Allocation allocationOut) {

        // Copies data from camera preview buffer
        inputAllocation.copyFrom(dataIn);

        // Converts input image to RGBA
        if (useCustomYUVToRGBConversion)
            customYUVToRGBAConverter.forEach_convert(midAllocation);
        else
            sYUV.forEach(midAllocation);

        // Executes our custom script calculations
        scriptCMain.forEach_root(allocationOut);

        // Waits for all kernels to end
        mRS.finish();

        // Sends custom script output to rendering surface
        allocationOut.ioSend();
    }

    public RSCompute(RenderScript rsContext, Point inputImageSize, Point outputAllocationSize) {
        mRS = rsContext;
        mInputImageSize = inputImageSize;
        mOutputAllocationSize = outputAllocationSize;

        // Initalizes Scripts
        if (useCustomYUVToRGBConversion)
            customYUVToRGBAConverter = new ScriptC_customYUVToRGBAConverter(mRS);
        else
            sYUV = ScriptIntrinsicYuvToRGB.create(mRS, Element.U8_4(mRS));

        scriptCMain = new ScriptC_main(mRS);

        // Init Allocations

        // Calculates expected YUV bytes count as YUV is not a human friendly way of storing data:
        // https://en.wikipedia.org/wiki/YUV#Y.27UV420p_.28and_Y.27V12_or_YV12.29_to_RGB888_conversion
        int expectedBytes = inputImageSize.x * inputImageSize.y *
                ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;

        Type.Builder yuvType = new Type.Builder(mRS, Element.U8(mRS)).setX(expectedBytes);
        inputAllocation = Allocation.createTyped(mRS, yuvType.create(), Allocation.USAGE_SCRIPT);

        // Creates temporary allocation that will match camera preview size
        Type.Builder rgbaType = new Type.Builder(mRS, Element.RGBA_8888(mRS)).setX(mInputImageSize.x).setY(mInputImageSize.y);
        midAllocation = Allocation.createTyped(mRS, rgbaType.create(), Allocation.USAGE_SCRIPT);

        // Init compute
        if (useCustomYUVToRGBConversion) {
            customYUVToRGBAConverter.invoke_setInputImageSize(mInputImageSize.x, mInputImageSize.y);
            customYUVToRGBAConverter.set_inputAllocation(inputAllocation);
        } else
            sYUV.setInput(inputAllocation);

        scriptCMain.set_aIn(midAllocation);
        scriptCMain.set_sizeIn(new Int2(mInputImageSize.x, mInputImageSize.y)); // Tells the script camera preview size

        // Tells the script the resize ratio from input to output
        scriptCMain.set_scaleInv(1.0f / ((float) mOutputAllocationSize.x / (float) mInputImageSize.x));

        // Sets brightness threshold (0-255), so that gray values higher than it will be turned to red.
        scriptCMain.set_threshold(180);

    }


}