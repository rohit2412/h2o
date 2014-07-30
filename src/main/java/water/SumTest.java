package water;

import water.fvec.Chunk;


public class SumTest extends MRTask3<SumTest> {
    private double _totalSum;
    private double _partialSum;

    public double getSum() { return _totalSum; }

    @Override public void map(Chunk[] chunks) {
        _partialSum = 0;
        for( int row = 0; row < chunks[0]._len; row++ ) {
            double value = chunks[0].at0(row);
            _partialSum += value;
        }
        _totalSum += _partialSum;
    }

    @Override public void reduce(SumTest rhs) {
        _totalSum += rhs._totalSum;
    }
}