/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 - Alberto Marchetti <alberto.marchetti@hydex11.net>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.hydex11.profilerexample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.renderscript.*;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "RSProfiler";

    // The following bool defines if you need pure profiling mode.
    // If true:
    // - Log check interval will be set to a high value (20 seconds)
    // - Application will automatically end after n cycles (defined below)
    private static final boolean PURE_PROFILING = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Starts the example
        example();
    }

    Thread exampleThread;

    // Instantiates our profiler
    Timings timings;

    private void example() {
        // Prevent window dimming
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        timings = new Timings(this);
        try {
            timings.enableSaveStats(true);
        } catch (IOException e) {
            throw new RuntimeException("Could not create temporary CSV file", e);
        }

        //if (!PURE_PROFILING) {
            LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearLayout);

            // Create a view to see LogCat log
            LogView logView = new LogView(this, Timings.TAG, PURE_PROFILING ? 20 : 5);
            logView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            logView.addLogLine("Wait for logs. It is going to take some seconds...\n");

            // Add our console view to the window
            linearLayout.addView(logView);
        //}

        // Set the only view button to kill our application
        Button endMe = (Button) findViewById(R.id.button);
        endMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (exampleThread != null)
                    // We force the interruption of loop thread (may cause an exception,
                    // but this is just a RS example!
                    exampleThread.interrupt();

                System.exit(0);
            }
        });

        // Button to send current stats CSV file
        Button sendStatsBtn = (Button) findViewById(R.id.saveStatsBtn);
        sendStatsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    timings.sendStats();
                    System.exit(0);
                } catch (IOException e) {
                    Log.d(TAG, "CSV file send error");
                }
            }
        });

        // As we are going over a loop, it is needed to not run it on UI thread, as we'd
        // get frozen window rendering. So, just make another one.
        exampleThread = new Thread(new Runnable() {
            @Override
            public void run() {

                // Instantiate our RS context
                final RenderScript mRS = RenderScript.create(MainActivity.this);

                // Load input image
                Bitmap inputBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.houseimage);

                // Instantiates the input allocation.
                Allocation inputAllocation = Allocation.createFromBitmap(mRS, inputBitmap);
                Allocation outputAllocation = Allocation.createTyped(mRS, inputAllocation.getType());

                // Allocation to store the rgba to gray conversion result
                Type.Builder tb = new Type.Builder(mRS, Element.U8(mRS));
                tb.setX(inputBitmap.getWidth());
                tb.setY(inputBitmap.getHeight());
                Allocation grayAllocation = Allocation.createTyped(mRS, tb.create());

                // Tells the profiler to call this function before taking each timing. This way
                // we are listening for previous kernel to really end.
                timings.setTimingCallback(new Timings.TimingCallback() {
                    @Override
                    public void run() {
                        mRS.finish();
                    }
                });

                // Averaging will run every 10 cycles
                timings.setTimingDebugInterval(50);

                if (PURE_PROFILING) {
                    // After n total samples, application will exit and saved CSV data will be sent
                    timings.setStatsSaveCountLimit(16000);
                }

                // We create two different scripts, that have same kernels. First one is
                // standard RenderScript, second one uses FilterScript approach. This way
                // you can see differences in performance.
                ScriptC_main main = new ScriptC_main(mRS);
                ScriptC_main_fs main_fs = new ScriptC_main_fs(mRS);

                main.set_inputAllocation(inputAllocation);
                main.set_grayAllocation(grayAllocation);
                main.set_outputAllocation(outputAllocation);
                main_fs.set_inputAllocation(inputAllocation);
                main_fs.set_outputAllocation(outputAllocation);

                main.set_width(inputBitmap.getWidth());
                main.set_height(inputBitmap.getHeight());
                main_fs.set_width(inputBitmap.getWidth());
                main_fs.set_height(inputBitmap.getHeight());

                int blurRadius3x3 = 1;
                int blurRadius7x7 = 3;
                int blurRadius15x15 = 7;

                // Here we set the launch options for the kernels, to prevent the
                // blur pointers from overflowing
                Script.LaunchOptions launchOptionsBlur3x3 = new Script.LaunchOptions();
                launchOptionsBlur3x3.setX(blurRadius3x3, inputBitmap.getWidth() - 1 - blurRadius3x3);
                launchOptionsBlur3x3.setY(blurRadius3x3, inputBitmap.getHeight() - 1 - blurRadius3x3);
                Script.LaunchOptions launchOptionsBlur7x7 = new Script.LaunchOptions();
                launchOptionsBlur7x7.setX(blurRadius7x7, inputBitmap.getWidth() - 1 - blurRadius7x7);
                launchOptionsBlur7x7.setY(blurRadius7x7, inputBitmap.getHeight() - 1 - blurRadius7x7);
                Script.LaunchOptions launchOptionsBlur15x15 = new Script.LaunchOptions();
                launchOptionsBlur15x15.setX(blurRadius15x15, inputBitmap.getWidth() - 1 - blurRadius15x15);
                launchOptionsBlur15x15.setY(blurRadius15x15, inputBitmap.getHeight() - 1 - blurRadius15x15);

                // My loop
                while (true) {
                    // Calling this function, the profiler sets current time as initial one
                    timings.initTimings();

                    // Here we test three different sets of kernels, increasing the blur radius.
                    // The more it gets high, the more neighboring elements are accessed in the process.

                    // Blur 3x3
                    main.set_blurRadius(blurRadius3x3);
                    main_fs.set_blurRadius(blurRadius3x3);

                    // Reset is called as set_ functions took some time
                    timings.resetLastTimingsTimestamp();

                    // Adds timing for kernel
                    main.forEach_blurSimpleKernel(outputAllocation, launchOptionsBlur3x3);
                    timings.addTiming("blur3x3");

                    main.forEach_blurPointerKernel(inputAllocation, outputAllocation, launchOptionsBlur3x3);
                    timings.addTiming("blur3x3 - (pointers)");

                    main.forEach_blurPointerKernelSet(inputAllocation, launchOptionsBlur3x3);
                    timings.addTiming("blur3x3 - (pointers|rsSet)");

                    main.forEach_blurPointerKernelGet(outputAllocation, launchOptionsBlur3x3);
                    timings.addTiming("blur3x3 - (pointers|rsGet)");

                    main_fs.forEach_blurSimpleKernel(outputAllocation, launchOptionsBlur3x3);
                    timings.addTiming("blur3x3 - FilterScript");
                    
                    main.forEach_setValuesSimpleKernel(inputAllocation, launchOptionsBlur3x3);
                    timings.addTiming("setValues3x3");

                    main.forEach_setValuesPointerKernel(inputAllocation, outputAllocation, launchOptionsBlur3x3);
                    timings.addTiming("setValues3x3 - (pointers)");

                    main.forEach_setValuesPointerKernelSet(inputAllocation, launchOptionsBlur3x3);
                    timings.addTiming("setValues3x3 - (pointers|rsSet)");

                    main_fs.forEach_setValuesSimpleKernel(outputAllocation, launchOptionsBlur3x3);
                    timings.addTiming("setValues3x3 - FilterScript");

                    // Blur 7x7
                    main.set_blurRadius(blurRadius7x7);
                    main_fs.set_blurRadius(blurRadius7x7);

                    // Reset is called as set_ functions took some time
                    timings.resetLastTimingsTimestamp();

                    // Adds timing for kernel
                    main.forEach_blurSimpleKernel(outputAllocation, launchOptionsBlur7x7);
                    timings.addTiming("blur7x7");

                    main.forEach_blurPointerKernel(inputAllocation, outputAllocation, launchOptionsBlur7x7);
                    timings.addTiming("blur7x7 - (pointers)");

                    main.forEach_blurPointerKernelSet(inputAllocation, launchOptionsBlur7x7);
                    timings.addTiming("blur7x7 - (pointers|rsSet)");

                    main.forEach_blurPointerKernelGet(outputAllocation, launchOptionsBlur7x7);
                    timings.addTiming("blur7x7 - (pointers|rsGet)");

                    main_fs.forEach_blurSimpleKernel(outputAllocation, launchOptionsBlur7x7);
                    timings.addTiming("blur7x7 - FilterScript");

                    main.forEach_setValuesSimpleKernel(inputAllocation, launchOptionsBlur7x7);
                    timings.addTiming("setValues7x7");

                    main.forEach_setValuesPointerKernel(inputAllocation, outputAllocation, launchOptionsBlur7x7);
                    timings.addTiming("setValues7x7 - (pointers)");

                    main.forEach_setValuesPointerKernelSet(inputAllocation, launchOptionsBlur7x7);
                    timings.addTiming("setValues7x7 - (pointers|rsSet)");

                    main_fs.forEach_setValuesSimpleKernel(outputAllocation, launchOptionsBlur7x7);
                    timings.addTiming("setValues7x7 - FilterScript");

                    // Blur 15x15
                    main.set_blurRadius(blurRadius15x15);
                    main_fs.set_blurRadius(blurRadius15x15);

                    // Reset is called as set_ functions took some time
                    timings.resetLastTimingsTimestamp();

                    // Adds timing for kernel
                    main.forEach_blurSimpleKernel(outputAllocation, launchOptionsBlur15x15);
                    timings.addTiming("blur15x15");

                    main.forEach_blurPointerKernel(inputAllocation, outputAllocation, launchOptionsBlur15x15);
                    timings.addTiming("blur15x15 - (pointers)");

                    main.forEach_blurPointerKernelSet(inputAllocation, launchOptionsBlur15x15);
                    timings.addTiming("blur15x15 - (pointers|rsSet)");

                    main.forEach_blurPointerKernelGet(outputAllocation, launchOptionsBlur15x15);
                    timings.addTiming("blur15x15 - (pointers|rsGet)");

                    main_fs.forEach_blurSimpleKernel(outputAllocation, launchOptionsBlur15x15);
                    timings.addTiming("blur15x15 - FilterScript");

                    main.forEach_setValuesSimpleKernel(inputAllocation, launchOptionsBlur15x15);
                    timings.addTiming("setValues15x15");

                    main.forEach_setValuesPointerKernel(inputAllocation, outputAllocation, launchOptionsBlur15x15);
                    timings.addTiming("setValues15x15 - (pointers)");

                    main.forEach_setValuesPointerKernelSet(inputAllocation, launchOptionsBlur15x15);
                    timings.addTiming("setValues15x15 - (pointers|rsSet)");

                    main_fs.forEach_setValuesSimpleKernel(outputAllocation, launchOptionsBlur15x15);
                    timings.addTiming("setValues15x15 - FilterScript");

                    // RGBA to GRAY conversion
                    main.forEach_rgbaToGrayNoPointer(inputAllocation, grayAllocation);
                    timings.addTiming("RGBAtoGRAY");

                    main.forEach_rgbaToGrayPointerAndSet(inputAllocation);
                    timings.addTiming("RGBAtoGRAY - (pointers|rsSet)");

                    main.forEach_rgbaToGrayPointerAndGet(grayAllocation);
                    timings.addTiming("RGBAtoGRAY - (pointers|rsGet)");

                    main.forEach_rgbaToGrayPointerAndOut(inputAllocation, grayAllocation);
                    timings.addTiming("RGBAtoGRAY - (pointers)");

                    main_fs.forEach_rgbaToGrayNoPointer(inputAllocation, grayAllocation);
                    timings.addTiming("RGBAtoGRAY - FilterScript");

                    //if (!PURE_PROFILING) {
                        // Checks if this cycle is the correct one for debugging timings and outputs them
                        // in case it is.
                        timings.debugTimings();

                        try {
                            // Small wait, to not overkill the CPU/GPU
                            Thread.sleep(10, 0);
                        } catch (InterruptedException e) {
                            // Will be caused by clicking on "End" button, as we will be interrupting
                            // this Thread brutally

                        }
                    //}
                }
            }
        });
        exampleThread.start();
    }

}
