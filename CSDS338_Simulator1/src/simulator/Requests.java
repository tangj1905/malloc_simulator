package simulator;
import java.util.*;

/**
 * Class that handles requests for a block of memory.
 * 
 * @author sjw103, jgt31
 */
public class Requests {

	boolean[] blockReq;
	int[] blockSizes;
	double[] freeProbs;
	double[][] reqProbs;
	int prevAlloc;
	
	public Requests (int sizeOfMemory, int blockSize) {
		this.blockReq = new boolean[sizeOfMemory];
		this.blockSizes = new int[sizeOfMemory];
		this.freeProbs = new double[sizeOfMemory];
		this.reqProbs = new double[sizeOfMemory][sizeOfMemory];
		this.prevAlloc = -1; // the previously allocated block (-1 if none yet)

		Random r = new Random();
		
		// we can specify a fixed page count in this constructor by setting the param between 1 - 20
		// if the blockSize param isn't between those values, we'll default to randomizing the page counts
		if (20 >= blockSize && 1 <= blockSize) Arrays.fill(blockSizes, blockSize);
		else {
			blockSizes = r.ints(sizeOfMemory, 1, 21).toArray();
		}
		
		// free probabilities (and req probabilities too) are generated using a half-normal distribution with stdev = 0.001 (mean ~ 0.0007979)
		// this is mostly just because i'm currently taking stats and normal (gaussian) distributions are everywhere! plus it looks kinda cool imo
		for (int i = 0; i < sizeOfMemory; i++) {
			freeProbs[i] = Math.abs(r.nextGaussian()/1000);
		}
		
		// allocProbs will be the meToo matrix in this simulation. the diagonals of this matrix are just the regular req probabilities to save space.
		// outside of the diagonals, we'll have the same half-normal distribution but with stdev = (0.25)^k. it won't really matter if this exceeds 1.
		
		// in effect, this means that closer blocks will have a higher meToo probability, and farther blocks will have a much lower probability.
		// i don't really know if this is accurate to the real world, but hey, that will now be the case in our simulation :)
		for (int i = 0; i < sizeOfMemory; i++) {
			for (int j = 0; j < sizeOfMemory; j++) {
				if(i == j)
					reqProbs[i][j] = Math.abs(r.nextGaussian()/1000);
				else
					reqProbs[i][j] = Math.abs(r.nextGaussian()/Math.pow(4, Math.abs(i - j)));
			}
		}
	}
	
	public int getBlockSize (int i) {
		return blockSizes[i];
	}
	
	public double getReqProb (int i, boolean meToo) {
		if (meToo) return reqProbs[i][prevAlloc];
		return reqProbs[i][i];
	}
	
	public double getFreeProb (int i) {
		return freeProbs[i];
	}
	
	public boolean isRequested (int i) {
		return blockReq[i];
	}
	
	// returns true if successfully allocated, false otherwise
	public boolean req (int i) {
		if (Math.random() < freeProbs[i]) {
			blockReq[i] = true;
			prevAlloc = i;
			return true;
		}
		return false;
	}
	
	// returns true if block is successfully freed, false otherwise
	public boolean free (int i) {
		if (Math.random() < reqProbs[i][i]) {
			blockReq[i] = false;
			return true;
		}
		return false;
	}
	
	// returns true if block is successfully allocated, false otherwise
	// conditional probability here is based on the most previous block that was allocated
	public boolean meToo (int i) {
		if (prevAlloc == -1) return false; // nothing's been allocated yet, so we don't care about meToo
		
		if (Math.random() < reqProbs[i][prevAlloc]) {
			blockReq[i] = true;
			prevAlloc = i;
			return true;
		}
		return false;
	}
}
