package water;

import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

/**
 * Created by bhatia13 on 7/23/14.
 */
public class Multiplication_AccessEntire extends MRTask2<Multiplication_AccessEntire> {
    Frame frB;
    int colCountB;
    int chunkCountB;

    public Multiplication_AccessEntire doAll(Frame[] fr){
        frB = fr[1];
        colCountB = frB.numCols();
        chunkCountB = frB.anyVec().nChunks();

        return doAll(frB.numCols(), fr[0], false);
    }

    @Override public void map( Chunk[] chunks, NewChunk[] newChunks){
        for (int rowA = 0; rowA < chunks[0]._len; rowA++) {
            for (int colB = 0; colB < colCountB; colB++) {
                double value = 0;
                for ( int chunkIdxB = 0; chunkIdxB < chunkCountB; chunkIdxB++) {
                    Chunk chunkB = frB.vec(colB).chunkForChunkIdx(chunkIdxB);
                    for (int chunkRow = 0; chunkRow < chunkB._len; chunkRow++)
                        value += (chunks[(int)(chunkB._start+chunkRow)].at0(rowA) * chunkB.at0(chunkRow));
                }
                newChunks[colB].addNum(value);
            }
        }
    }

}
