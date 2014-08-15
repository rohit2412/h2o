package water;

import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

/**
 * Created by bhatia13 on 7/23/14.
 */
public class Multiplication extends MRTask3<Multiplication> {

    @Override
    public Multiplication  doAll(Frame[] fr){
        int numOutputs = (int)fr[1].numCols();
        return doAll(numOutputs, fr, false, fr[0].anyVec().nChunks(), false);
    }

    @Override public void setupCommData(int newLoB){
        try {
            int bcolPerAchunk = (int)Math.floor((float)_frs[1].numCols()/_frs[0].anyVec().nChunks());
            _currStartB = newLoB * bcolPerAchunk;
            _currLenB = (newLoB==_frs[1].numCols()-1) ? (_frs[1].numCols()-bcolPerAchunk*(_frs[1].numCols()-1)) : bcolPerAchunk;

            Chunk[][] bvs = new Chunk[_currLenB][_frs[1].anyVec().nChunks()];
            for (int i = _currStartB; i < _currStartB+_currLenB; i++) {
                Vec v = _frs[1].vec(i);
                for (int j = 0; j<_frs[1].anyVec().nChunks(); j++) {
                    bvs[i-_currStartB][j] = v.chunkForChunkIdx(j);
                }
            }

            _commData = new double[_currLenB][(int)_frs[1].numRows()];
            for (int i = 0; i < bvs.length; i++) {
                for (int j = 0; j < bvs[0].length; j++) {
                    int chunkStart=(int)bvs[i][j]._start;
                    for (int k=0; k<bvs[i][j]._len; k++)
                        _commData[i][chunkStart+k] = bvs[i][j].at0(k);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e){
            e.printStackTrace();
        }
    }


    /* Data to pass to the predecesser chunk. Thread with Chunk 0 has to pass a suitable chunk.
     */
    public double[][] getCommData(int round){
        return _commData;
    }


    @Override public void map( Chunk[] chunks, NewChunk[] newChunks, int startElemChunkB, int chunkLenB){
        for (int rowA = 0; rowA < chunks[0]._len; rowA++) {
            for (int rowB = 0; rowB < chunkLenB; rowB++) {
                double value = 0;
                for ( int col = 0; col < chunks.length; col++) {
                         value += (chunks[col].at0(rowA) * _commData[rowB][col]);
                }
                newChunks[startElemChunkB+rowB].addNum(value);
            }
        }
    }

}
