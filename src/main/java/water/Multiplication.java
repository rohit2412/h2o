package water;

import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

/**
 * Created by bhatia13 on 7/23/14.
 */
public class Multiplication extends MRTask3<Multiplication> {

    @Override
    public Multiplication  doAll(Frame[] fr){
        //Transpose the second matrix to do multiplication row by row
        fr[1] = new Transpose().doAll( new Frame[]{fr[1]}, true, 1).outputFrame(null, null);

        //Swap the matrix with less chunks in front, Noting if the output is transposed in the process
        boolean isOutputTransposed=false;
        if ( fr[0].anyVec().nChunks() > fr[1].anyVec().nChunks() ) {
            Frame tempFr = fr[1]; fr[1]=fr[0]; fr[0]=tempFr; isOutputTransposed=true;
        }

        int numOutputs = isOutputTransposed ? 0 : (int)fr[1].numRows();
        return doAll(numOutputs, fr, false, fr[1].anyVec().nChunks(), isOutputTransposed);
    }

    @Override public void setupCommData(int newLoB){
        try {
            Chunk bvs[] = new Chunk[_frs[1].numCols()];
            for (int i = 0; i < _frs[1].numCols(); i++) {
                bvs[i] = _frs[1].vecs()[i].chunkForChunkIdx(newLoB);
            }

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


    /* Data to pass to the predecesser chunk. Thread with Chunk 0 has to pass a suitable chunk.
     */
    public double[][] getCommData(int round){
        if (_lo==0) {
            int newLoB = ( (round+_nChunksA-1) <  _numRoundsB )  ?  (round+_nChunksA-1)  :  (round+_nChunksA - 1 - _numRoundsB);
            setupCommData(newLoB);
        }
        return _commData;
    }


    @Override public void map( Chunk[] chunks, NewChunk[] newChunks, int startElemChunkB, int chunkLenB){
        for (int rowA = 0; rowA < chunks[0]._len; rowA++) {
            for (int rowB = 0; rowB < chunkLenB; rowB++) {
                double value = 0;
                for ( int col = 0; col < chunks.length; col++) {
                         value += (chunks[col].at0(rowA) * _commData[rowB][col]);
                }
                if (isOutputTransposed) addToOutputVec(rowB, value);
                else newChunks[startElemChunkB+rowB].addNum(value);
            }
        }
    }

}
