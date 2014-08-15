package samples;

import water.*;
import water.fvec.*;

/**
 * Sample for MRTask3
 */
public class Sample07_MRTask3 {
    public static void main(String[] args) throws Exception {
        water.Boot.main(UserCode.class, args);
        Runtime.getRuntime().halt(1);
    }

    public static class UserCode {
        public static void userMain(String[] args) throws Exception {
            H2O.main(args);
            int size = Integer.parseInt(args[0]);
            int op = Integer.parseInt(args[1]);
            int m=size;
            int n=size;
            int k=size;

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

            switch (op) {
                case 1:
                    Transpose transpose = new Transpose();
                    long startTime = System.currentTimeMillis();
                    transpose.doAll(new Frame[]{frame1}, true, 1);
                    Frame frame2 = transpose.outputFrame(null, null);
                    long endTime = System.currentTimeMillis();
                    System.out.println("Time Taken Transpose: " + (endTime - startTime));
                    break;
//
//
//
//            DualMatrixTask addition = new DualMatrixTask();
//            addition.doAll(frame.numCols(), new Frame[]{frame,frame2}, new DualMatrixTask.H2oAdd()) ;
//            Frame frame3 = addition.outputFrame(null, null);
//
//            transpose = new Transpose();
//            Frame frame4 = transpose.doAll(new Frame[]{frame3}, true, 1).outputFrame(null, null);

                case 2:
                    Multiplication multiplication = new Multiplication();
                    startTime = System.currentTimeMillis();
                    Frame frame5 = multiplication.doAll(new Frame[]{frame, frame1}).outputFrame(null, null);
                    endTime = System.currentTimeMillis();
                    System.out.println("Time Taken Mult-MRTask3: " + (endTime - startTime));
                    break;

                case 3:
                    Multiplication_AccessEntire multiplicationA = new Multiplication_AccessEntire();
                    startTime = System.currentTimeMillis();
                    Frame frame6 = multiplicationA.doAll(new Frame[]{frame, frame1}).outputFrame(null, null);
                    endTime = System.currentTimeMillis();
                    System.out.println("Time Taken Mult-AccessEntire: " + (endTime - startTime));
                    break;

                case 4:
                    Multiplication_CacheChunkObjects multiplicationB = new Multiplication_CacheChunkObjects();
                    startTime = System.currentTimeMillis();
                    Frame frame7 = multiplicationB.doAll(new Frame[]{frame, frame1}).outputFrame(null, null);
                    endTime = System.currentTimeMillis();
                    System.out.println("Time Taken Mult-CacheChunks: " + (endTime - startTime));
                    break;
                case 5:
                    Multiplication_StoreEntire multiplicationC = new Multiplication_StoreEntire();
                    startTime = System.currentTimeMillis();
                    Frame frame8 = multiplicationC.doAll(new Frame[]{frame, frame1}).outputFrame(null, null);
                    endTime = System.currentTimeMillis();
                    System.out.println("Time Taken Mult-StoreEntire: " + (endTime - startTime));
            }
//            System.out.println(frame5.toStringAll());
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
            int chkSize = 50;
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




