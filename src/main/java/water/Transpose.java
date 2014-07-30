package water;

import water.fvec.Chunk;

//Transpose
public class Transpose extends MRTask3<Transpose> {
    @Override public void map(Chunk[] chunks) {
        for ( int origCol = 0; origCol < chunks.length; origCol++) {
            for (int origRow = 0; origRow < chunks[0]._len; origRow++) {
                double value = chunks[origCol].at0(origRow);
                int newCol = origRow;
                addToOutputVec(newCol, value);
            }
        }
    }
}