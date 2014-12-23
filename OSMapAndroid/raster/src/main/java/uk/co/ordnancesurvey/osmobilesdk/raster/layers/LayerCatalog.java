package uk.co.ordnancesurvey.osmobilesdk.raster.layers;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class LayerCatalog {

    private static final String[] DEFAULT_LAYERS =
            new String[]{"SV", "SVR", "50K", "50KR", "250K", "250KR", "MS", "MSR", "OV2", "OV1", "OV0"};
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

            // Undocumented projects extracted from JavaScript:
            //   resolutionLookup = '''{"SV": [1.0, 250],"SVR": [2.0, 250],"50K": [5.0, 200],"50KR": [10.0, 200],"250K": [25.0, 200],"250KR": [50.0, 200],"MS": [100.0, 200],"MSR": [200.0, 200],"OV2": [500.0, 200],"OV1": [1000.0, 200],"OV0": [2500.0, 200],"VMD": [2.5, 200],"VMDR": [4.0, 250],"25K": [2.5, 200],"25KR": [4.0, 250],"VML": [1.0, 250],"VMLR": [2.0, 250],"10KBW": [1.0, 250],"10KBWR": [2.0, 250],"10KR": [2.0, 250],"10K": [1.0, 250],"CS00": [896.0, 250],"CS01": [448.0, 250],"CS02": [224.0, 250],"CS03": [112.0, 250],"CS04": [56.0, 250],"CS05": [28.0, 250],"CS06": [14.0, 250],"CS07": [7.0, 250],"CS08": [3.5, 250],"CS09": [1.75, 250],"CS10": [0.875, 250],"CSG06": [14.0, 250],"CSG07": [7.0, 250],"CSG08": [3.5, 250],"CSG09": [1.75, 250]}'''
            //   for k,v in sorted(json.loads(resolutionLookup).iteritems()): print '{%-10s%5g, %3r},'%('"%s",'%k,v[0],v[1])
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
    public final static Comparator<Layer> COMPARE_METRES_PER_PIXEL = new MetresPerPixelComparator();
    public final static Comparator<Layer> COMPARE_METRES_PER_PIXEL_REVERSED = Collections.reverseOrder(COMPARE_METRES_PER_PIXEL);

    private final static class MetresPerPixelComparator implements Comparator<Layer> {
        @Override
        public int compare(Layer lhs, Layer rhs) {
            return Float.compare(lhs.getMetresPerPixel(), rhs.getMetresPerPixel());
        }
    }

    public static Layer[] layersForProductCodes(String[] productCodes) {
        Arrays.sort(productCodes);
        ArrayList<Layer> list = new ArrayList<Layer>();
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

    public static Layer[] getDefaultLayers() {
        return layersForProductCodes(DEFAULT_LAYERS);
    }
}
