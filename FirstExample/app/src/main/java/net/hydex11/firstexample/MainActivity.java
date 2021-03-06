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

package net.hydex11.firstexample;

import android.support.v7.app.AppCompatActivity;
import android.renderscript.*;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Run the example
        example();
    }

    private void example() {

        // Instantiates the RenderScript context
        RenderScript mRS = RenderScript.create(this);

        // Creates an input array, containing some numbers
        int inputArray[] = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        // Instantiates the input Allocation, which will contain our sample numbers
        Allocation inputAllocation = Allocation.createSized(mRS, Element.I32(mRS), inputArray.length);

        // Copies the input array into the input Allocation
        inputAllocation.copyFrom(inputArray);

        // Instantiates the output Allocation, which will contain the result of the process
        Allocation outputAllocation = Allocation.createSized(mRS, Element.I32(mRS), inputArray.length);

        // Instantiates the sum script
        ScriptC_sum myScript = new ScriptC_sum(mRS);

        // Run the sum process, taking elements that are inside inputAllocation and
        // placing the process results inside the outputAllocation
        myScript.forEach_sum2(inputAllocation, outputAllocation);

        // Copies the result of the process from the outputAllocation to
        // a simple int array
        int outputArray[] = new int[inputArray.length];
        outputAllocation.copyTo(outputArray);

        String debugString = "Output: ";
        for (int i = 0; i < outputArray.length; i++)
            debugString += String.valueOf(outputArray[i]) + (i < outputArray.length - 1 ? ", " : "");

        TextView textView = (TextView) findViewById(R.id.debugTextView);
        textView.setText(debugString);

    }
}
