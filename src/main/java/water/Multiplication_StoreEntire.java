package water;

import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

/**
 * Created by bhatia13 on 7/23/14.
 */
public class Multiplication_StoreEntire extends MRTask2<Multiplication_StoreEntire> {
    double[][] frB;  //Cols x Rows

    public Multiplication_StoreEntire doAll(Frame[] fr){
        frB = new double[fr[1].numCols()][(int) fr[1].numRows()];
        for (int colB = 0; colB < fr[1].numCols(); colB++) {
            int rowB=0;
            int chunksB = fr[1].anyVec().nChunks();
            for ( int chunkIdxB = 0; chunkIdxB < chunksB; chunkIdxB++) {
                Chunk chunkB = fr[1].vec(colB).chunkForChunkIdx(chunkIdxB);
                for (int chunkRow=0; chunkRow<chunkB._len; chunkRow++, rowB++)
                    frB[colB][rowB]=chunkB.at0(chunkRow);
            }
        }
        return doAll(fr[1].numCols(), fr[0], false);
    }

    @Override public void map( Chunk[] chunks, NewChunk[] newChunks){
        int rowCountB = frB[0].length;
        int colCountB  = frB.length;
        for (int colB = 0; colB < colCountB; colB++) {
            for (int rowA = 0; rowA < chunks[0]._len; rowA++) {
                double value = 0;
                    for (int rowB = 0; rowB < rowCountB; rowB++) {
                        value += (chunks[rowB].at0(rowA) * frB[colB][rowB]);    //colA=rowB
                    }
                newChunks[colB].addNum(value);
            }
        }
    }
}
