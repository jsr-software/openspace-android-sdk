/**
 * OpenSpace Android SDK Licence Terms
 *
 * The OpenSpace Android SDK is protected by © Crown copyright – Ordnance Survey 2013.[https://github.com/OrdnanceSurvey]
 *
 * All rights reserved (subject to the BSD licence terms as follows):.
 *
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * Neither the name of Ordnance Survey nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 *
 */
package uk.co.ordnancesurvey.osmobilesdk.raster.layers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class LayerCatalog {

    private static final String[] DEFAULT_LAYERS =
            new String[]{"SV", "SVR", "50K", "50KR", "250K",
                    "250KR", "MS", "MSR", "OV2", "OV1", "OV0"};

    private static Layer[] ALL_LAYERS = new Layer[]{
            new Layer("SV", "1", 250, 250), // Street view
            new Layer("SVR", "2", 250, 500), // Street view
            new Layer("VMD", "2.5", 200, 500), // Vector Map District
            new Layer("VMDR", "4", 250, 1000), // Vector Map District
            new Layer("50K", "5", 200, 1000), // 1:50k
            new Layer("50KR", "10", 200, 2000), // 1:50k
            new Layer("250K", "25", 200, 5000), // 1:250k
            new Layer("250KR", "50", 200, 10000), // 1:250k
            new Layer("MS", "100", 200, 20000), // 1:1M
            new Layer("MSR", "200", 200, 40000), // 1:1M
            new Layer("OV2", "500", 200, 100000), // Overview 2
            new Layer("OV1", "1000", 200, 200000), // Overview 1
            new Layer("OV0", "2500", 200, 500000), // Overview 0

            // Pro products
            new Layer("VML", "1", 250, 250), // Vector Map Local
            new Layer("VMLR", "2", 250, 500), // Vector Map Local
            new Layer("25K", "2.5", 200, 500), // 1:25k
            new Layer("25KR", "4", 250, 1000), // 1:25k

            // Zoom products
            new Layer("CS00", "896", 250, 224000),
            new Layer("CS01", "448", 250, 112000),
            new Layer("CS02", "224", 250, 56000),
            new Layer("CS03", "112", 250, 28000),
            new Layer("CS04", "56", 250, 14000),
            new Layer("CS05", "28", 250, 7000),
            new Layer("CS06", "14", 250, 3500),
            new Layer("CS07", "7", 250, 1750),
            new Layer("CS08", "3.5", 250, 875),
            new Layer("CS09", "1.75", 250, 437.5f),
            new Layer("CS10", "0.875", 250, 218.75f),

            // Undocumented projects
            new Layer("10K", "1", 250, 250),
            new Layer("10KBW", "1", 250, 250),
            new Layer("10KBWR", "2", 250, 500),
            new Layer("10KR", "2", 250, 500),
            new Layer("CSG06", "14", 250, 3500),
            new Layer("CSG07", "7", 250, 1750),
            new Layer("CSG08", "3.5", 250, 875),
            new Layer("CSG09", "1.75", 250, 437.5f),

            new Layer("25K-660DPI", null, 260, 250), // 1:25k
            new Layer("25K-330DPI", null, 260, 500), // 1:25k
            new Layer("25K-165DPI", null, 260, 1000), // 1:25k
            new Layer("50K-660DPI", null, 260, 500), // 1:50k
            new Layer("50K-330DPI", null, 260, 1000), // 1:50k
            new Layer("50K-165DPI", null, 260, 2000), // 1:50k
    };
    private final static Comparator<Layer> COMPARE_METRES_PER_PIXEL
            = new MetresPerPixelComparator();
    private final static Comparator<Layer> COMPARE_METRES_PER_PIXEL_REVERSED
            = Collections.reverseOrder(COMPARE_METRES_PER_PIXEL);
    private final static class MetresPerPixelComparator implements Comparator<Layer> {
        @Override
        public int compare(Layer lhs, Layer rhs) {
            return Float.compare(lhs.getMetresPerPixel(), rhs.getMetresPerPixel());
        }
    }

    public static Layer[] getDefaultLayers() {
        return getLayers(DEFAULT_LAYERS);
    }

    public static Layer[] getLayers(String[] productCodes) {
        Arrays.sort(productCodes);
        ArrayList<Layer> list = new ArrayList<>();
        for (Layer layer : ALL_LAYERS) {
            boolean isContained = (Arrays.binarySearch(productCodes, layer.getProductCode()) >= 0);
            if (isContained) {
                list.add(layer);
            }
        }
        Layer[] ret = list.toArray(new Layer[0]);
        Arrays.sort(ret, COMPARE_METRES_PER_PIXEL);
        return ret;
    }

    public static Comparator<? super Layer> getReverseComparator() {
        return COMPARE_METRES_PER_PIXEL_REVERSED;
    }


}
