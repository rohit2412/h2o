package water;


import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

//DualMatrixTask - For Elementwise Binary operators like Addition and Multiplication over Matrices
public class DualMatrixTask extends MRTask3<DualMatrixTask> {

    H2oOperator op;

    public DualMatrixTask doAll( int outputs, Frame[] fr, H2oOperator op){
        this.op = op;
        return doAll(outputs, fr);
    }

    @Override public void setupCommData(int newLoB){
        try {
            Chunk bvs[] = new Chunk[_frs[1].numCols()];
            for (int i = 0; i < _frs[1].numCols(); i++)
                bvs[i] = _frs[1].vecs()[i].chunkForChunkIdx(_lo);

            _commData = new double[bvs[0]._len][_frs[1].numCols()];
            for (int i = 0; i < _commData.length; i++) {
                for (int j = 0; j < _commData[0].length; j++) {
                    _commData[i][j] = bvs[j].at0(i);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e){
            e.printStackTrace();
        }
    }

    @Override public void map(Chunk[] chunks, NewChunk[] newChunks) {
        for (int row = 0; row < chunks[0]._len; row++) {
            for ( int col = 0; col < chunks.length; col++) {
                double value = chunks[col].at0(row);
                newChunks[col].addNum( op.apply(value, _commData[row][col])) ;
            }
        }
    }


    public static abstract class H2oOperator extends Iced{
        public abstract double apply (double a, double b);
    }

    public static class H2oAdd extends H2oOperator {
        @Override
        public double apply(double a, double b) {
            return a+b;
        }
    }

    public static class H2oSub extends H2oOperator {
        @Override
        public double apply(double a, double b) {
            return a-b;
        }
    }

    public static class H2oMult extends H2oOperator {
        @Override
        public double apply(double a, double b) {
            return a*b;
        }
    }

    public static class H2oPow extends H2oOperator {
        @Override
        public double apply(double a, double b) {
            return Math.pow(a,b);
        }
    }

    public static class H2oDiv extends H2oOperator {
        @Override
        public double apply(double a, double b) {
            return a/b;
        }
    }
}