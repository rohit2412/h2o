package samples;

import water.*;
import water.fvec.*;

import java.io.File;
import java.io.PrintWriter;

/**
 * Sample for MRTask3
 */
public class Sample07_MRTask3 {
    public static void main(String[] args) throws Exception {
        water.Boot.main(UserCode.class, args);
    }

    public static class UserCode {
        public static void userMain(String[] args) throws Exception {
            H2O.main(args);
            int m=700;
            int n=800;
            int k=700;

            double[][] rows1 = new double[m][n];
            double[][] rows3 = new double[n][k];
            for (int i=0;i<m;i++){
                for (int j=0;j<n;j++){
                    rows1[i][j] = i+j;
                }
            }

            for (int i=0;i<n;i++){
                for (int j=0;j<k;j++){
                    rows3[i][j]=(i<=j)?1:0;
                }
            }

//            Key key = Key.make("MyFrame");
//            Key key1 = Key.make("MyFrame1");
            Frame frame = create(null, null, rows1);
            Frame frame1 = create(null, null, rows3);


            Transpose transpose = new Transpose();
            transpose.doAll(new Frame[]{frame1}, true, 1);
            Frame frame2 = transpose.outputFrame(null, null);



            DualMatrixTask addition = new DualMatrixTask();
            addition.doAll(frame.numCols(), new Frame[]{frame,frame2}, new DualMatrixTask.H2oAdd()) ;
            Frame frame3 = addition.outputFrame(null, null);

            transpose = new Transpose();
            Frame frame4 = transpose.doAll(new Frame[]{frame3}, true, 1).outputFrame(null, null);


            Multiplication multiplication = new Multiplication();
            Frame frame5 = multiplication.doAll(new Frame[]{frame, frame4}).outputFrame(null, null);
            System.out.println(frame5.toStringAll());
        }


    }

    /**
     * Creates a frame programmatically.
     */
    public static Frame create(Key key, String[] headers, double[][] rows) {
        Futures fs = new Futures();
        Vec[] vecs = new Vec[rows[0].length];
        Key keys[] = new Vec.VectorGroup().addVecs(vecs.length);

        for( int c = 0; c < vecs.length; c++ ) {
            int chkSize = 100;
            AppendableVec vec = new AppendableVec(keys[c]);
            int cidx=0;
            NewChunk chunk = new NewChunk(vec,0);
            for( int r = 0; r < rows.length; r++ ) {
                chunk.addNum(rows[r][c]);
                if ((r+1)%chkSize ==0 ) {
                    chunk.close(cidx, fs);
                    cidx++;
                    chunk = new NewChunk(vec,cidx);}
            }
            chunk.close(cidx, fs);
            vecs[c] = vec.close(fs);
        }
        fs.blockForPending();
        return new Frame(key, headers, vecs);
    }

}




