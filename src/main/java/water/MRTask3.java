package water;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinPool;
import water.H2O.H2OCountedCompleter;
import water.fvec.*;
import water.fvec.Vec.VectorGroup;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Map/Reduce style distributed computation.
 * <br>
 * MRTask3 provides several <code>map</code> and <code>reduce</code> methods that can be
 * overriden to specify a computation. Several instances of this class will be
 * created to distribute the computation over F/J threads and machines.  Non-transient
 * fields are copied and serialized to instances created for map invocations. Reduce
 * methods can store their results in fields. Results are serialized and reduced all the
 * way back to the invoking node. When the last reduce method has been called, fields
 * of the initial MRTask3 instance contains the computation results.
 * <br>
 * Apart from small reduced POJO returned to the calling node, MRTask3 can
 * produce output vector(s) as a result.  These will have chunks co-located
 * with the input dataset, however, their number of lines will generally
 * differ, (so they won't be strictly compatible with the original). To produce
 * output vectors, call doAll.dfork version with required number of outputs and
 * override appropriate <code>map</code> call taking required number of
 * NewChunks.  MRTask3 will automatically close the new Appendable vecs and
 * produce an output frame with newly created Vecs.
 * <br>
 * In addition to MRTask2's functionality, MRTask3 can take multiple frames
 * as input, produce transposed output if required and provides a framework for
 * different threads/nodes to communicate 1 regular chunk-sized block after each mapper call.
 * With control over the number of rounds, MRTask3 is although more complex, but also more powerful than MRTask2.
 */
public abstract class MRTask3<T extends MRTask3<T>> extends DTask implements Cloneable, ForkJoinPool.ManagedBlocker {
    public MRTask3() { }
    public MRTask3(H2OCountedCompleter completer){super(completer); }

    /** The Vectors to work on. */
    public Frame _fr;
    // Hold Multiple Frames if required. The First frame is the same as _fr
    public Frame[] _frs;

    // appendables are treated separately (roll-ups computed in map/reduce style, can not be passed via K/V store).
    protected AppendableVec [] _appendables;
    private int _vid;
    private int _noutputs;
    // If TRUE, run entirely local - which will pull all the data locally.
    private boolean _run_local;

    private byte _priority = H2O.MIN_PRIORITY;
    @Override public byte priority() { return _priority; }
    private void raisePriority() {
        // Always 1 higher priority than calling thread... because the caller will
        // block & burn a thread waiting for this MRTask3 to complete.
        Thread cThr = Thread.currentThread();
        _priority = (byte)((cThr instanceof H2O.FJWThr) ? ((H2O.FJWThr)cThr)._priority+1 : super.priority());
    }


    public Frame outputFrame(String [] names, String [][] domains){ return outputFrame(null,names,domains); }
    public Frame outputFrame(Key key, String [] names, String [][] domains){
        Futures fs = new Futures();
        Frame res = outputFrame(key, names, domains, fs);
        fs.blockForPending();
        return res;
    }
    public Frame outputFrame(Key key, String [] names, String [][] domains, Futures fs){
        if (isOutputTransposed) return transposedOutputFrame(names);
        if(_noutputs == 0)return null;
        Vec [] vecs = new Vec[_noutputs];
        for(int i = 0; i < _noutputs; ++i) {
            if( _appendables==null )  // Zero rows?
                vecs[i] = _fr.anyVec().makeZero();
            else {
                _appendables[i]._domain = domains==null ? null : domains[i];
                vecs[i] = _appendables[i].close(fs);
            }
        }
        return new Frame(key,names,vecs);
    }

    public Frame transposedOutputFrame(String[] names){
        return transposedOutputFrame(null, names);
    }


    public Frame transposedOutputFrame(Key key, String[] names){
        return new Frame(key,names,_outputVecs);
    }


    /** Override with your map implementation.  This overload is given a single
     *  <strong>local</strong> input Chunk.  It is meant for map/reduce jobs that use a
     *  single column in a input Frame.  All map variants are called, but only one is
     *  expected to be overridden. */
    public void map( Chunk c ) { }
    public void map( Chunk c, NewChunk nc ) { }

    /** Override with your map implementation.  This overload is given two
     *  <strong>local</strong> Chunks.  All map variants are called, but only one
     *  is expected to be overridden. */
    public void map( Chunk c0, Chunk c1 ) { }
    public void map( Chunk c0, Chunk c1, NewChunk nc) { }
    public void map( Chunk c0, Chunk c1, NewChunk nc1, NewChunk nc2 ) { }

    /** Override with your map implementation.  This overload is given three
     * <strong>local</strong> input Chunks.  All map variants are called, but only one
     * is expected to be overridden. */
    public void map( Chunk c0, Chunk c1, Chunk c2 ) { }
    public void map( Chunk c0, Chunk c1, Chunk c2, NewChunk nc ) { }
    public void map( Chunk c0, Chunk c1, Chunk c2, NewChunk nc1, NewChunk nc2 ) { }

    /** Override with your map implementation.  This overload is given an array
     *  of <strong>local</strong> input Chunks, for Frames with arbitrary column
     *  numbers.  All map variants are called, but only one is expected to be
     *  overridden. */
    public void map(    Chunk cs[] ) { }
    public void map(    Chunk cs[], NewChunk nc ) { }
    public void map(    Chunk cs[], NewChunk nc1, NewChunk nc2 ) { }
    public void map(    Chunk cs[], NewChunk [] ncs ) { }

    public void map(    Chunk cs[], NewChunk [] ncs, int startElemChunkB, int chunkLenB) { }


    /** Override to combine results from 'mrt' into 'this' MRTask3.  Both 'this'
     *  and 'mrt' are guaranteed to either have map() run on them, or be the
     *  results of a prior reduce().  Reduce is optional if, e.g., the result is
     *  some output vector.  */
    public void reduce( T mrt ) { }

    /** Override to do any remote initialization on the 1st remote instance of
     *  this object, for initializing node-local shared data structures.
     *  Setup Communication Buffer and Semaphores. */
    protected void setupLocal() {
        MRTask3._commBuffer.put(_taskId, new ConcurrentHashMap<Integer, double[][]>());
        MRTask3._commBufferEmpty.put(_taskId, new ConcurrentHashMap<Integer, Semaphore>());
        MRTask3._commBufferFull.put(_taskId, new ConcurrentHashMap<Integer, Semaphore>());
        MRTask3._commCurrStartB.put(_taskId, new ConcurrentHashMap<Integer, Integer>());
        MRTask3._commCurrLenB.put(_taskId, new ConcurrentHashMap<Integer, Integer>());
    } // load the vecs in non-racy way (we will definitely need them and in case we don;t have cached version there will be unnecessary racy update from multiple maps at the same time)!
    /** Override to do any remote cleaning on the last remote instance of
     *  this object, for disposing of node-local shared data structures.
     *  Delete Communication Buffer and Semaphores. */
    protected void closeLocal() {
        MRTask3._commBuffer.remove(_taskId);
        MRTask3._commBufferEmpty.remove(_taskId);
        MRTask3._commBufferFull.remove(_taskId);
        MRTask3._commCurrStartB.remove(_taskId);
        MRTask3._commCurrLenB.remove(_taskId);
    }

    /** Internal field to track a range of remote nodes/JVMs to work on */
    protected short _nxx, _nhi;   // Range of Nodes to work on - remotely
    private int addShift( int x ) { x += _nxx; int sz = H2O.CLOUD.size(); return x < sz ? x : x-sz; }
    private int subShift( int x ) { x -= _nxx; int sz = H2O.CLOUD.size(); return x <  0 ? x+sz : x; }
    /** Internal field to track the left and right remote nodes/JVMs to work on */
    transient protected RPC<T> _nleft, _nrite;
    /** Internal field to track if this is a top-level local call */
    transient protected boolean _topLocal; // Top-level local call, returning results over the wire
    /** Internal field to track a range of local Chunks to work on */
    transient protected int _lo, _hi;   // Range of Chunks to work on - locally
    /** Internal field to track the left and right sub-range of chunks to work on */
    transient protected T _left, _rite; // In-progress execution tree

    transient private T _res;           // Result

    /** We can add more things to block on - in case we want a bunch of lazy
     *  tasks produced by children to all end before this top-level task ends.
     *  Semantically, these will all complete before we return from the top-level
     *  task.  Pragmatically, we block on a finer grained basis. */
    transient protected Futures _fs; // More things to block on

    // Profiling support.  Time for each subpart of a single M/R task, plus any
    // nested MRTasks.  All numbers are CTM stamps or millisecond times.
    private static class MRProfile extends Iced {
        String _clz;
        public MRProfile(MRTask3 mrt) {
            _clz = mrt.getClass().toString();
            _localdone = System.currentTimeMillis();
        }
        // See where these are set to understand their meaning.  If we split the
        // job, then _lstart & _rstart are the start of left & right jobs.  If we
        // do NOT split, then _rstart is 0 and _lstart is for the user map job(s).
        long _localstart, _rpcLstart, _rpcRstart, _rpcRdone, _localdone; // Local setup, RPC network i/o times
        long _mapstart, _userstart, _closestart, _mapdone; // MAP phase
        long _onCstart, _reducedone, _remoteBlkDone, _localBlkDone, _onCdone; // REDUCE phase
        // If we split the job left/right, then we get a total recording of the
        // last job, and the exec time & completion time of 1st job done.
        long _time1st, _done1st;
        int _size_rez0, _size_rez1; // i/o size in bytes during reduce
        MRProfile _last;
        long sumTime() { return _onCdone - (_localstart==0 ? _mapstart : _localstart); }
        void gather( MRProfile p, int size_rez ) {
            p._clz=null;
            if( _last == null ) _last=p;
            else {
                MRProfile first = _last._onCdone <= p._onCdone ? _last : p;
                MRProfile  last = _last._onCdone >  p._onCdone ? _last : p;
                _last = last;
                if( first._onCdone > _done1st ) { _time1st = first.sumTime(); _done1st = first._onCdone; }
            }
            if( size_rez !=0 )        // Record i/o result size
                if( _size_rez0 == 0 ) {      _size_rez0=size_rez; }
                else { /*assert _size_rez1==0;*/ _size_rez1=size_rez; }
            assert _last._onCdone >= _done1st;
        }

        @Override public String toString() { return toString(new StringBuilder(),0).toString(); }
        private StringBuilder toString(StringBuilder sb, int d) {
            if( d==0 ) sb.append(_clz).append("\n");
            for( int i=0; i<d; i++ ) sb.append("  ");
            if( _localstart != 0 ) sb.append("Node local ").append(_localdone - _localstart).append("ms, ");
            if( _userstart == 0 ) {   // Forked job?
                sb.append("Slow wait ").append(_mapstart-_localdone).append("ms + work ").append(_last.sumTime()).append("ms, ");
                sb.append("Fast work ").append(_time1st).append("ms + wait ").append(_onCstart-_done1st).append("ms\n");
                _last.toString(sb,d+1); // Nested slow-path print
                for( int i=0; i<d; i++ ) sb.append("  ");
                sb.append("join-i/o ").append(_onCstart-_last._onCdone).append("ms, ");
            } else {                  // Leaf map call?
                sb.append("Map ").append(_mapdone - _mapstart).append("ms (prep ").append(_userstart - _mapstart);
                sb.append("ms, user ").append(_closestart-_userstart);
                sb.append("ms, closeChk ").append(_mapdone-_closestart).append("ms), ");
            }
            sb.append("Red ").append(_onCdone - _onCstart).append("ms (locRed ");
            sb.append(_reducedone-_onCstart).append("ms");
            if( _remoteBlkDone!=0 ) {
                sb.append(", remBlk ").append(_remoteBlkDone-_reducedone).append("ms, locBlk ");
                sb.append(_localBlkDone-_remoteBlkDone).append("ms, close ");
                sb.append(_onCdone-_localBlkDone).append("ms, size ");
                sb.append(PrettyPrint.bytes(_size_rez0)).append("+").append(PrettyPrint.bytes(_size_rez1));
            }
            sb.append(")\n");
            return sb;
        }
    }
    MRProfile _profile;
    public String profString() { return _profile.toString(); }

    // Support for fluid-programming with strong types
    private final T self() { return (T)this; }

    /** Returns a Vec from the Frame.  */
    public final Vec vecs(int i) { return _fr.vecs()[i]; }

    /** Invokes the map/reduce computation over the given Vecs.  This call is
     *  blocking. */
    public final T doAll( Vec... vecs ) { return doAll(0,vecs); }
    public final T doAll(int outputs, Vec... vecs ) { return doAll(outputs,new Frame(null,vecs), false); }

    /** Invokes the map/reduce computation over the given Frame.  This call is
     *  blocking.  */
    public final T doAll( Frame fr, boolean run_local) { return doAll(0,fr, run_local); }
    public final T doAll( Frame fr ) { return doAll(0,fr, false); }
    public T doAll( Frame[] fr) {return doAll(0,fr, false, 1, false);}
    public final T doAll( Frame[] fr , boolean isOutputTransposed, int _numRounds) { return doAll(0,fr, false, _numRounds, isOutputTransposed); }
    public final T doAll( int outputs, Frame[] fr) {return doAll(outputs,fr, false, 1, false);}
    public final T doAll( int outputs, Frame[] fr , boolean isOutputTransposed){ return doAll(outputs,fr, false, 1, isOutputTransposed); }
    public final T doAll( int outputs, Frame[] fr , boolean isOutputTransposed, int _numRounds) { return doAll(outputs,fr, false, _numRounds, isOutputTransposed); }
    public final T doAll( int outputs, Frame fr) {return doAll(outputs,fr,false);}
    public final T doAll( int outputs, Frame[] fr, boolean run_local, int numRounds, boolean isOutputTransposed){
        _frs = fr;
        _numRoundsB = numRounds;                      // Number of Rounds of Computation/Communication. Based on Second Matrix-B
        _nChunksA = fr[0].anyVec().nChunks();         // Number of chunks in Matrix-A. Equals the number fo mapper threads
        _taskId = new Random().nextInt();             // Random ID for the Task, Shared across all instances of the task
        this.isOutputTransposed = isOutputTransposed;
        return doAll(outputs, fr[0], run_local);
    }
    public final T doAll( int outputs, Frame fr, boolean run_local) {
        dfork(outputs,fr, run_local);
        return getResult();
    }

    public final void asyncExec(Vec... vecs){asyncExec(0,new Frame(vecs),false);}
    public final void exec(Vec... vecs){exec(0, new Frame(vecs), false);}
    public final void asyncExec(Frame fr){asyncExec(0,fr,false);}
    public final void exec(Frame fr){exec(0, fr, false);}

    public final void exec( int outputs, Frame fr, boolean run_local){
        // Use first readable vector to gate home/not-home
        fr.checkCompatible();       // Check for compatible vectors
        if((_noutputs = outputs) > 0) _vid = fr.anyVec().group().reserveKeys(outputs);
        _fr = fr;                   // Record vectors to work on
        _nxx = (short)H2O.SELF.index(); _nhi = (short)H2O.CLOUD.size(); // Do Whole Cloud
        _run_local = run_local;     // Run locally by copying data, or run globally?
        setupLocal0();              // Local setup
        compute2();
    }

    /**
     * Fork the task in strictly non-blocking fashion.
     *
     * Same functionality as dfork, but does not raise priority, so user is should
     * *never* block on it
     */
    public final void asyncExec( int outputs, Frame fr, boolean run_local){
        // Use first readable vector to gate home/not-home
        fr.checkCompatible();       // Check for compatible vectors
        if((_noutputs = outputs) > 0) _vid = fr.anyVec().group().reserveKeys(outputs);
        _fr = fr;                   // Record vectors to work on
        _nxx = (short)H2O.SELF.index(); _nhi = (short)H2O.CLOUD.size(); // Do Whole Cloud
        _run_local = run_local;     // Run locally by copying data, or run globally?
        setupLocal0();              // Local setup
        H2O.submitTask(this);       // Begin normal execution on a FJ thread
    }
    /** Invokes the map/reduce computation over the given Frame. This call is
     *  asynchronous.  It returns 'this', on which getResult() can be invoked
     *  later to wait on the computation.  */
    public final T dfork( Vec...vecs ) {return dfork(0,vecs);}
    public T dfork( Frame fr ) {return dfork(0,fr,false);}
    public final T dfork( int outputs, Vec... vecs) {
        return dfork(outputs,new Frame(vecs),false);
    }
    public final T dfork( int outputs, Frame fr, boolean run_local) {
        raisePriority();
        asyncExec(outputs,fr,run_local);
        return self();
    }

    /** Block for and get any final results from a dfork'd MRTask3.
     *  Note: the desired name 'get' is final in ForkJoinTask.  */
    public final T getResult() {
        try { ForkJoinPool.managedBlock(this); } catch( InterruptedException e ) { }
        return self();
    }

    // Return true if blocking is unnecessary, which is true if the Task isDone.
    public boolean isReleasable() {  return isDone();  }
    // Possibly blocks the current thread.  Returns true if isReleasable would
    // return true.  Used by the FJ Pool management to spawn threads to prevent
    // deadlock is otherwise all threads would block on waits.
    public boolean block() {
        while( !isDone() ) join();
        return true;
    }

    /** Called once on remote at top level, probably with a subset of the cloud.
     *  Called internal by D/F/J.  Not expected to be user-called.  */
    @Override public final void dinvoke(H2ONode sender) {
        setupLocal0();              // Local setup
        compute2();                 // Do The Main Work
        // nothing here... must do any post-work-cleanup in onCompletion
    }

    // Setup for local work: fire off any global work to cloud neighbors; do all
    // chunks; call user's init.
    private final void setupLocal0() {
        assert _profile==null;
        _fs = new Futures();
        _profile = new MRProfile(this);
        _profile._localstart = System.currentTimeMillis();
        _topLocal = true;
        // Check for global vs local work
        int selfidx = H2O.SELF.index();
        int nlo = subShift(selfidx);
        assert nlo < _nhi;
        final int nmid = (nlo+_nhi)>>>1; // Mid-point
        if( !_run_local && nlo+1 < _nhi ) { // Have global work?
            _profile._rpcLstart = System.currentTimeMillis();
            _nleft = remote_compute(nlo+1,nmid);
            _profile._rpcRstart = System.currentTimeMillis();
            _nrite = remote_compute( nmid,_nhi);
            _profile._rpcRdone  = System.currentTimeMillis();
        }
        _lo = 0;  _hi = _fr.anyVec().nChunks(); // Do All Chunks
        // If we have any output vectors, make a blockable Futures for them to
        // block on.
        // get the Vecs from the K/V store, to avoid racing fetches from the map calls
        _fr.vecs();
        setupLocal();                     // Setup any user's shared local structures
        _profile._localdone = System.currentTimeMillis();
    }

    // Make an RPC call to some node in the middle of the given range.  Add a
    // pending completion to self, so that we complete when the RPC completes.
    private final RPC<T> remote_compute( int nlo, int nhi ) {
        // No remote work?
        if( !(nlo < nhi) ) return null;
        int node = addShift(nlo);
        assert node != H2O.SELF.index();
        T rpc = clone();
        rpc.setCompleter(null);
        rpc._nhi = (short)nhi;
        addToPendingCount(1);       // Not complete until the RPC returns
        // Set self up as needing completion by this RPC: when the ACK comes back
        // we'll get a wakeup.
        return new RPC(H2O.CLOUD._memary[node], rpc).addCompleter(this).call();
    }

    int _numRoundsB =1;
    int _CHKSize = 100;
    int _nChunksA;

    /*  Variables and Methods to control the transposed output vectors if required. Current Chunk, it's index and length
     *  are all stored for the output vectors.
     */
    public boolean isOutputTransposed = false;
    Vec[] _outputVecs;
    Key[] _outputVecKeys;
    AppendableVec[] _tempOutputVecs;
    NewChunk[] _currChunks;
    int[] _currChunkidx;
    int[] _currChunkLen;

    public void initOutputVec() {
        int outputVecCount = (int) _fr.anyVec().length();
        _outputVecs = new Vec[outputVecCount];
        _outputVecKeys = new Vec.VectorGroup().addVecs(_outputVecs.length);
        _tempOutputVecs = new AppendableVec[outputVecCount];
        _currChunks = new NewChunk[outputVecCount];
        _currChunkidx = new int[outputVecCount];
        _currChunkLen = new int[outputVecCount];
    }

    public void addToOutputVec( int vecIdx, double d){
        vecIdx += _fr.anyVec().chunk2StartElem(_lo);
        if (_currChunks[vecIdx] == null) {
            _tempOutputVecs[vecIdx] = new AppendableVec(_outputVecKeys[vecIdx]);
            _currChunkidx[vecIdx]=0;
            _currChunks[vecIdx] = new NewChunk( _tempOutputVecs[vecIdx], _currChunkidx[vecIdx]);
        }

        if (_currChunkLen[vecIdx] == _CHKSize){
            _currChunks[vecIdx].close( _currChunkidx[vecIdx], _fs);
            _currChunkLen[vecIdx]=0;
            _currChunkidx[vecIdx]++;
            _currChunks[vecIdx] = new NewChunk( _tempOutputVecs[vecIdx], _currChunkidx[vecIdx]);
        }
        _currChunks[vecIdx].addNum(d);
        _currChunkLen[vecIdx]++;
    }

    private void closeOutputVecs() {
        for (int vecIdx=0; vecIdx<_outputVecs.length; vecIdx++) {
            if (_currChunks[vecIdx] != null)   {
                _currChunks[vecIdx].close(_currChunkidx[vecIdx], _fs);
                _outputVecs[vecIdx] = _tempOutputVecs[vecIdx].close(_fs);
                _currChunks[vecIdx] = null;
            }
        }
    }

    private void reduceOutputVecs(Vec[] outputVecsNew) {
        for (int vecIdx=0; vecIdx<_outputVecs.length; vecIdx++) {
            if (outputVecsNew[vecIdx] != null) _outputVecs[vecIdx] = outputVecsNew[vecIdx];
        }
    }



    /* Variables and Methods to provide data Communication between chunks.
     * Synchronized by ConcurrentHashMap for different chunk Indeces and with two binary semaphores
     * as in Producer-Consumer Paradigm.
     */

    int _taskId;
    public static HashMap<Integer, ConcurrentHashMap<Integer,double[][]>> _commBuffer = new HashMap<Integer, ConcurrentHashMap<Integer, double[][]>>();
    public transient double[][] _commData;
    public static HashMap<Integer, ConcurrentHashMap<Integer, Semaphore>> _commBufferEmpty = new HashMap<Integer, ConcurrentHashMap<Integer, Semaphore>>();
    public static HashMap<Integer, ConcurrentHashMap<Integer, Semaphore>> _commBufferFull = new HashMap<Integer, ConcurrentHashMap<Integer, Semaphore>>();
    public static HashMap<Integer, ConcurrentHashMap<Integer, Integer>> _commCurrStartB = new HashMap<Integer, ConcurrentHashMap<Integer, Integer>>();
    public static HashMap<Integer, ConcurrentHashMap<Integer, Integer>> _commCurrLenB = new HashMap<Integer, ConcurrentHashMap<Integer, Integer>>();
    public int _currStartB, _currLenB;

    // Initialize HashMaps in the default empty stage
    private static synchronized void initHashMap(int _taskId, int _lo){
        Semaphore full, empty;
        if (MRTask3._commBufferEmpty.get(_taskId).get(_lo)==null) {
            empty = new Semaphore(1);
            MRTask3._commBufferEmpty.get(_taskId).put(_lo, empty);
        }
        if (MRTask3._commBufferFull.get(_taskId).get(_lo)==null) {
            full = new Semaphore(1);
            MRTask3._commBufferFull.get(_taskId).put(_lo, full);
            try {
                full.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // SetupCommData and load data from Chunk newLoB in Matrix-B
    public void setupCommData(int newLoB){}
    public void setupComm(){
        setupCommData(_lo);
        MRTask3.initHashMap(_taskId, _lo);
    }

    // Get Communication Data to send to predecessor, Chunk 0 sends to _nChunksA-1
    public double[][] getCommData(int round){return null;}

    class DataSender<T extends DataSender> extends DTask {
        int chunkToComm, taskId; double[][] commData;
        int startB, lenB;
        public DataSender(int chunkToComm, int taskId, double[][] commData, int startB, int lenB) {
            this.chunkToComm=chunkToComm;
            this.taskId=taskId;
            this.commData=commData;
            this.startB=startB;
            this.lenB=lenB;
        }
        @Override
        public void compute2() {
            try {
                MRTask3.initHashMap(taskId, chunkToComm);
                MRTask3._commBufferEmpty.get(taskId).get(chunkToComm).acquire();
                MRTask3._commBuffer.get(taskId).put(chunkToComm, commData);
                MRTask3._commCurrStartB.get(taskId).put(chunkToComm, startB);
                MRTask3._commCurrLenB.get(taskId).put(chunkToComm, lenB);
                MRTask3._commBufferFull.get(taskId).get(chunkToComm).release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    // Send and Receive data in each round
    public void communicateToNeighbor(int round){
        try {
            final int chunkToComm = (_lo==0) ? (_nChunksA -1) : (_lo-1);
            H2ONode nodeToComm = _fr.anyVec().chunkKey(chunkToComm).home_node();


            if (nodeToComm.index() == H2O.SELF.index()) {new DataSender (chunkToComm, _taskId, getCommData(round), _currStartB, _currLenB).compute2();}
            else { new RPC(nodeToComm, new DataSender (chunkToComm, _taskId, getCommData(round), _currStartB, _currLenB)).call();}

            MRTask3._commBufferFull.get(_taskId).get(_lo).acquire();
            double[][] buffer = _commBuffer.get(_taskId).get(_lo);
            _commData = new double[buffer.length][buffer[0].length];
            for (int x=0; x<_commData.length; x++){
                for (int y=0; y<_commData[0].length; y++){
                    _commData[x][y] = buffer[x][y];
                }
            }
            _currStartB = _commCurrStartB.get(_taskId).get(_lo);
            _currLenB = _commCurrLenB.get(_taskId).get(_lo);
            MRTask3._commBufferEmpty.get(_taskId).get(_lo).release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected long _t0;
    /** Called from FJ threads to do local work.  The first called Task (which is
     *  also the last one to Complete) also reduces any global work.  Called
     *  internal by F/J.  Not expected to be user-called.  */
    @Override public final void compute2() {
        _t0 = System.nanoTime();
        assert _left == null && _rite == null && _res == null;
        _profile._mapstart = System.currentTimeMillis();
        if( _hi-_lo >= 2 ) { // Multi-chunk case: just divide-and-conquer to 1 chunk
            final int mid = (_lo+_hi)>>>1; // Mid-point
            _left = clone();
            _rite = clone();
            _left._profile = new MRProfile(this);
            _rite._profile = new MRProfile(this);
            _left._hi = mid;          // Reset mid-point
            _rite._lo = mid;          // Also set self mid-point
            addToPendingCount(1);     // One fork awaiting completion
            _left.fork();             // Runs in another thread/FJ instance
            _rite.compute2();         // Runs in THIS F/J thread
            _profile._mapdone = System.currentTimeMillis();
            return;                   // Not complete until the fork completes
        }
        // Zero or 1 chunks, and further chunk might not be homed here
        if( _hi > _lo ) {           // Single chunk?
            Vec v0 = _fr.anyVec();
            if( _run_local || v0.chunkKey(_lo).home() ) { // And chunk is homed here?
                // Make decompression chunk headers for these chunks
                Vec vecs[] = _fr.vecs();
                Chunk bvs[] = new Chunk[vecs.length];
                NewChunk [] appendableChunks = null;
                for( int i=0; i<vecs.length; i++ )
                    if( vecs[i] != null ) {
                        assert _run_local || vecs[i].chunkKey(_lo).home()
                                : "Chunk="+_lo+" v0="+v0+", k="+v0.chunkKey(_lo)+"   v["+i+"]="+vecs[i]+", k="+vecs[i].chunkKey(_lo);
                        bvs[i] = vecs[i].chunkForChunkIdx(_lo);
                    }
                if (isOutputTransposed){ initOutputVec(); }
                else if(_noutputs > 0){
                    final VectorGroup vg = vecs[0].group();
                    _appendables = new AppendableVec[_noutputs];
                    appendableChunks = new NewChunk[_noutputs];
                    for(int i = 0; i < _appendables.length; ++i){
                        _appendables[i] = new AppendableVec(vg.vecKey(_vid+i));
                        appendableChunks[i] = (NewChunk)_appendables[i].chunkForChunkIdx(_lo);
                    }
                }
                setupComm();
                for (int round=0; round< _numRoundsB;round++) {
                    // Call all the various map() calls that apply
                    _profile._userstart = System.currentTimeMillis();
                    if (_fr.vecs().length == 1) map(bvs[0]);
                    if (_fr.vecs().length == 2) map(bvs[0], bvs[1]);
                    if (_fr.vecs().length == 3) map(bvs[0], bvs[1], bvs[2]);
                    if (true) map(bvs);
                    if (_noutputs == 1) { // convenience versions for cases with single output.
                        if (_fr.vecs().length == 1) map(bvs[0], appendableChunks[0]);
                        if (_fr.vecs().length == 2) map(bvs[0], bvs[1], appendableChunks[0]);
                        if (_fr.vecs().length == 3) map(bvs[0], bvs[1], bvs[2], appendableChunks[0]);
                        if (true) map(bvs, appendableChunks[0]);
                    }
                    if (_noutputs == 2) { // convenience versions for cases with 2 outputs (e.g split).
                        if (_fr.vecs().length == 1) map(bvs[0], appendableChunks[0], appendableChunks[1]);
                        if (_fr.vecs().length == 2) map(bvs[0], bvs[1], appendableChunks[0], appendableChunks[1]);
                        if (_fr.vecs().length == 3)
                            map(bvs[0], bvs[1], bvs[2], appendableChunks[0], appendableChunks[1]);
                        if (true) map(bvs, appendableChunks[0], appendableChunks[1]);
                    }
                    map(bvs, appendableChunks);
                    if (_numRoundsB > 1 && _frs!=null && _frs.length>1) {
                        map(bvs, appendableChunks, _currStartB, _currLenB);
                        if (round < _numRoundsB - 1) {
                            communicateToNeighbor(round + 1);
                        }
                    }
                }
                _res = self();          // Save results since called map() at least once!
                if (isOutputTransposed){ closeOutputVecs(); }

                // Further D/K/V put any new vec results.
                _profile._closestart = System.currentTimeMillis();
                for( Chunk bv : bvs ) bv.close(_lo,_fs);
                if(_noutputs > 0) for(NewChunk nch:appendableChunks)nch.close(_lo, _fs);
            }
        }
        _profile._mapdone = System.currentTimeMillis();
        tryComplete();              // And this task is complete
    }

    /** OnCompletion - reduce the left and right into self.  Called internal by
     *  F/J.  Not expected to be user-called. */
    @Override public final void onCompletion( CountedCompleter caller ) {
        _profile._onCstart = System.currentTimeMillis();
        // Reduce results into 'this' so they collapse going up the execution tree.
        // NULL out child-references so we don't accidentally keep large subtrees
        // alive since each one may be holding large partial results.
        reduce2(_left); _left = null;
        reduce2(_rite); _rite = null;
        // Only on the top local call, have more completion work
        _profile._reducedone = System.currentTimeMillis();
        if( _topLocal ) postLocal();
        _profile._onCdone = System.currentTimeMillis();
    }

    // Call 'reduce' on pairs of mapped MRTask3's.
    // Collect all pending Futures from both parties as well.
    private void reduce2( MRTask3<T> mrt ) {
        if( mrt == null ) return;
        _profile.gather(mrt._profile,0);
        if( _res == null ) _res = mrt._res;
        else if( mrt._res != null ) _res.reduce4(mrt._res);
        // Futures are shared on local node and transient (so no remote updates)
        assert _fs == mrt._fs;
    }

    protected void postGlobal(){}
    // Work done after all the main local work is done.
    // Gather/reduce remote work.
    // Block for other queued pending tasks.
    // Copy any final results into 'this', such that a return of 'this' has the results.
    private final void postLocal() {
        reduce3(_nleft);            // Reduce global results from neighbors.
        reduce3(_nrite);
        _profile._remoteBlkDone = System.currentTimeMillis();
        _fs.blockForPending();
        _profile._localBlkDone = System.currentTimeMillis();
        // Finally, must return all results in 'this' because that is the API -
        // what the user expects
        int nlo = subShift(H2O.SELF.index());
        int nhi = _nhi;             // Save before copyOver crushes them
        if( _res == null ) _nhi=-1; // Flag for no local results *at all*
        else if( _res != this ) {   // There is a local result, and its not self
            _res._profile = _profile; // Use my profile (not childs)
            copyOver(_res);           // So copy into self
        }
        closeLocal();
        if( nlo==0 && nhi == H2O.CLOUD.size() ) {
            // Do any post-writing work (zap rollup fields, etc)
            _fr.reloadVecs();
            for( int i=0; i<_fr.numCols(); i++ )
                _fr.vecs()[i].postWrite();
            postGlobal();
        }
    }

    // Block for RPCs to complete, then reduce global results into self results
    private void reduce3( RPC<T> rpc ) {
        if( rpc == null ) return;
        T mrt = rpc.get();          // This is a blocking remote call
        // Note: because _fs is transient it is not set or cleared by the RPC.
        // Because the MRT object is a clone of 'self' it's likely to contain a ptr
        // to the self _fs which will be not-null and still have local pending
        // blocks.  Not much can be asserted there.
        _profile.gather(mrt._profile, rpc.size_rez());
        // Unlike reduce2, results are in mrt directly not mrt._res.
        if( mrt._nhi != -1L ) {     // Any results at all?
            if( _res == null ) _res = mrt;
            else _res.reduce4(mrt);
        }
    }

    /** Call user's reduction.  Also reduce any new AppendableVecs.  Called
     *  internal by F/J.  Not expected to be user-called.  */
    protected void reduce4( T mrt ) {
        // Reduce any AppendableVecs
        if( _noutputs > 0 )
            for( int i=0; i<_appendables.length; i++ )
                _appendables[i].reduce(mrt._appendables[i]);

        if (isOutputTransposed) reduceOutputVecs(mrt._outputVecs);

        // User's reduction
        reduce(mrt);
    }

    /** Cancel/kill all work as we can, then rethrow... do not invisibly swallow
     *  exceptions (which is the F/J default).  Called internal by F/J.  Not
     *  expected to be user-called.  */
    @Override public final boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller ) {
        if( _nleft != null ) _nleft.cancel(true); _nleft = null;
        if( _nrite != null ) _nrite.cancel(true); _nrite = null;
        _left = null;
        _rite = null;
        return super.onExceptionalCompletion(ex, caller);
    }

    /** Local Clone - setting final-field completer */
    @Override public T clone() {
        T x = (T)super.clone();
        x.setCompleter(this); // Set completer, what used to be a final field
        x._topLocal = false;  // Not a top job
        x._nleft = x._nrite = null;
        x. _left = x. _rite = null;
        x._fs = _fs;
        x._profile = null;    // Clone needs its own profile
        x.setPendingCount(0); // Volatile write for completer field; reset pending count also
        return x;
    }
}
