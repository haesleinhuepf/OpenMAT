package de.mpicbg.imagej;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLDouble;
import com.jmatio.types.MLStructure;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.View;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.planar.PlanarImgs;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.scijava.search.web.ImageJForumSearcher;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 *
 * Author: Robert haesleinhuepf Haase, rhaase@mpi-cbg.de
 * July 2019 at MBL Woods Hole
 */
public class Open_MAT implements PlugIn {
    @Override
    public void run(String s) {
        String filename = IJ.getFilePath("MAT file loader");
        if (filename.length() > 0) {
            read(filename);
        }
    }

    public static void read(String filename) {
        MatFileReader mfr = new MatFileReader();
        try {
            mfr.read(new File(filename));
            MLArray content = mfr.getContent().get("IM_dat");
            parseArray(content, filename + " > ", "IM_dat");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void parseArray(MLArray content, String parent, String name) {
        if (content instanceof MLStructure) {
            MLStructure structure = (MLStructure) content;
            for (String key : structure.getFieldNames()) {
                MLArray array = structure.getField(key);
                parseArray(array, parent + "." + name, key);
            }
        } else if (content instanceof MLDouble) {
            MLDouble doubl = (MLDouble) content;
            Img<FloatType> img = parseImage(doubl);
            if (img != null) {
                // XYZC -> TXYC

                RandomAccessibleInterval<FloatType> view = img;
                long[] dims = new long[img.numDimensions()];
                img.dimensions(dims);
                System.out.println("dim before: " + Arrays.toString(dims));
                //view = Views.moveAxis(view, 1, 2);
                view = Views.moveAxis(view, 0, 2);
                //view = Views.moveAxis(view, 0, 1);
                view.dimensions(dims);
                System.out.println("dim after: " + Arrays.toString(dims));
                //view = Views.moveAxis(view, 1, 2);
                ImagePlus imp = ImageJFunctions.wrap(view, parent + "." + name);
                imp = new Duplicator().run(imp, 1, imp.getNChannels(), 1, imp.getNSlices(), 1, imp.getNFrames());
                imp.show();
                IJ.run(imp,"Enhance Contrast", "saturated=0.35");
            }
        }
    }

    private static Img<FloatType> parseImage(MLDouble doubl) {
        long[] dimensions = new long[doubl.getDimensions().length];

        for (int d = 0; d < dimensions.length; d++) {
            dimensions[d] = doubl.getDimensions()[dimensions.length - 1 - d];
        }
        if (dimensions.length > 0 && dimensions[0] > 1) {
            Img<FloatType> img = PlanarImgs.floats(dimensions);
            copyImage(img, doubl, 0, dimensions, new long[]{});
            return img;
        }
        return null;
    }

    private static int copyImage(Img<FloatType> img, MLDouble doubl, int offset, long[] dimensions, long[] position) {
        if (dimensions.length > 1) {
            long[] subdimensions = new long[dimensions.length - 1];
            System.arraycopy(dimensions, 1, subdimensions, 0, subdimensions.length);
            long[] subposition = new long[position.length + 1];
            if (position.length > 0) {
                System.arraycopy(position, 0, subposition, 0, position.length);
            }
            for (long d = 0; d < dimensions[0]; d++) {
                subposition[subposition.length - 1] = d;
                offset = copyImage(img, doubl, offset, subdimensions, subposition);
            }
        } else {
            long[] subposition = new long[position.length + 1];
            if (position.length > 0) {
                System.arraycopy(position, 0, subposition, 0, position.length);
            }
            RandomAccess<FloatType> ra = img.randomAccess();
            for (long d = 0; d < dimensions[0]; d++) {
                subposition[subposition.length - 1] = d;
                ra.setPosition(subposition);
                ra.get().set(doubl.get(offset).floatValue());
                offset++;
            }
        }
        return offset;
    }


    public static void main(String[] args) {
        new ImageJ();
        Open_MAT.read("C:/structure/data/test.mat");
    }
}
