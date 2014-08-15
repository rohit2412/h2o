package water;

import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

/**
 * Created by bhatia13 on 7/23/14.
 */
public class Multiplication_CacheChunkObjects extends MRTask2<Multiplication_CacheChunkObjects> {
    Frame frB;
    int colCountB;
    int chunkCountB;

    public Multiplication_CacheChunkObjects doAll(Frame[] fr){
        frB = fr[1];
        colCountB = frB.numCols();
        chunkCountB = frB.anyVec().nChunks();

        return doAll(frB.numCols(), fr[0], false);
    }

    @Override public void map( Chunk[] chunks, NewChunk[] newChunks){
        Chunk[] chunksB = new Chunk[chunkCountB];

        for (int colB = 0; colB < colCountB; colB++) {

            for ( int chunkIdxB = 0; chunkIdxB < chunkCountB; chunkIdxB++)
                chunksB[chunkIdxB] = frB.vec(colB).chunkForChunkIdx(chunkIdxB);

            for (int rowA = 0; rowA < chunks[0]._len; rowA++) {
                double value = 0;
                for ( int chunkIdxB = 0; chunkIdxB < chunkCountB; chunkIdxB++) {
                    Chunk chunkB = chunksB[chunkIdxB];
                    for (int chunkRow = 0; chunkRow < chunkB._len; chunkRow++)
                        value += (chunks[(int)(chunkB._start+chunkRow)].at0(rowA) * chunkB.at0(chunkRow));
                }
                newChunks[colB].addNum(value);
            }
        }
    }

}
