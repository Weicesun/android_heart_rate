package edu.gatech.ece6258.util;

import android.util.Log;

import edu.gatech.ece6258.util.tool.DoubleFft1d;

public class Fft {
	
	public static double FFT( double [] in, int size, double samplingFrequency) {
		//use r component to do
		double temp = 0;
		double maxPosition = 0;
		double frequency=0;
		double[] output = new double[2*size];
		
		for(int i=0;i<output.length;i++)
			output[i] = 0;
		
		for(int x=0;x<size;x++){
			output[x]=in[x];
		}
		DoubleFft1d fft = new DoubleFft1d(size);
		fft.realForward(output);
		for(int x=0;x<2*size;x++){
			output[x]= Math.abs(output[x]);
        //	Log.d("DEBUG Freq", "DEBUG - outputFFT[" + x + "]: " + output[x]);
		}
		
		for(int z=8; z<size; z++) { // 12 was chosen because it is a minimum frequency that we think people can get to determine heart rate.
        	if(temp < output[z]) {
        		temp = output[z];
        		maxPosition = z;
	    //    	Log.d("DEBUG Freq", "DEBUG - maxPosition: " + maxPosition);
        	}
        }
    	if (maxPosition < 12) {
    		maxPosition = 0;
    	}
		
        frequency = maxPosition*samplingFrequency/(2*size);

        return frequency;
	}
}
