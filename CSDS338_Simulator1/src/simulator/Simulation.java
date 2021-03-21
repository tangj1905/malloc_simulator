package simulator;
import java.util.*;
import java.io.*;

/**
 * Our main simulation class, which broadly handles allocation, deallocation, outputs, etc...
 * 
 * @authors sjw103, jgt31
 */
public class Simulation {
	
	/* OUR MAIN SET OF BLOCKS */
	static Requests blockreq;
	
	/* OUR FREELIST */
	static ArrayList<Integer[]> freeList;
	
	/* OUR OCCUPIED LISTS */
	static HashMap<Integer, Integer> occupiedList;
	static HashMap<Integer, Integer> occupiedIndex;
	
	/* DEFRAGMENTATION COUNTER */
	static int defragCounter;
	
	/* GLOBAL TIMER */
	static int timer;
	
	/* OUTPUT FILE WRITER */
	static FileWriter writer;
	
	/* DIVIDER STRING (for a nicer-looking output!) */
	final static String DIVIDER = "==================================================================================================";
	
	/* PARAMETERS THAT CAN BE CHANGED */
	final static int NUM_BLOCKS = 512;
	final static int BLOCK_SIZE = -1; // if not between 1 and 20, the sizes are randomized
	final static int NUM_PAGES = 4096;
	final static int SIMULATE_TIME = 1000000;
	final static boolean DEFRAG = true;
	final static double DEFRAG_THRESHOLD = 0.8;
	final static boolean OUTPUT_EVERYTHING = false; // if false, the program will only print defragmentation occurrences (useful for large trials)
	final static String OUTPUT_FILE = "output.txt";
	
	public static void main(String[] args) throws IOException {
		blockreq = new Requests(NUM_BLOCKS, BLOCK_SIZE);
		
		File f = new File(OUTPUT_FILE);
		
		new PrintWriter(f).close(); // clear the output file first
		
		writer = new FileWriter(f, true);
		
		writer.write(DIVIDER + "\n");
		writer.write("NUMBER OF BLOCKS: " + NUM_BLOCKS + "\n");
		writer.write("NUMBER OF PAGES IN EACH BLOCK:\n");
		
		for (int i = 0; i < NUM_BLOCKS; i++) {
			writer.write(blockreq.getBlockSize(i) + " ");
		}
		writer.write("\nNUMBER OF PAGES IN MEMORY: " + NUM_PAGES + "\n");
		writer.write(DIVIDER + "\n");
		
		// our free list will be a list of 2-element integer arrays of the form [startPage, endPage]. indexes are inclusive
		freeList = new ArrayList<Integer[]>();
		freeList.add(new Integer[]{0, NUM_PAGES - 1});
		
		// our occupied list will be a HashMap holding the block number along with the first page it occupies
		// our occupied index list will give the inverse relation here. though it might seem redundant, it's very useful for defragmentation
		occupiedList = new HashMap<Integer, Integer>();
		occupiedIndex = new HashMap<Integer, Integer>();
		
		defragCounter = 0;
		
		simulate();
		writer.flush();
		writer.close();
	}
	
	public static void simulate() throws IOException {
		Random r = new Random();
		timer = 0;
		
		while (timer <= SIMULATE_TIME) {
			// randomized starting index for block requests
			int sIndex = r.nextInt(NUM_BLOCKS);
			boolean activity = false;
			boolean success = true;
			
			for (int i = sIndex; i < sIndex + NUM_BLOCKS; i++) {
				int cIndex = i % NUM_BLOCKS;
				
				if (blockreq.isRequested(cIndex)) { // this block is occupied! we'll try and free it up first.
					if (blockreq.free(cIndex)) {
						dealloc(cIndex);
						activity = true;
						break;
					}
				} else { // this block is free! meToo goes first, then a normal req if that doesn't work
					if (blockreq.meToo(cIndex)) {
						success = allocate(cIndex, true);
						activity = true;
					} else if (blockreq.req(cIndex)) {
						success = allocate(cIndex, false);
						activity = true;
					}
					
					if (!success) { // this is bad! that means we can't fit this request in memory anywhere due to fragmentation
						writer.write("ERROR AT TIME " + timer + ", UNABLE TO ALLOCATE FOR A REQUEST.\n");
						writer.write("TOTAL DEFRAGMENTATION OVERHEAD: " + defragCounter);
						return;
					} else if (activity) break;
				}
			}
			
			if (activity) { // only update if something actually happened during this step
				double fragPercent = calculateFrag();
				if (OUTPUT_EVERYTHING) {
					writer.write("FRAGMENTATION RATIO AT TIME " + timer + ": " + fragPercent + "\n");
					writer.write("CURRENT FREELIST: " + fListString() + "\n");
				}
				if (fragPercent > DEFRAG_THRESHOLD && DEFRAG) defrag();
				if (OUTPUT_EVERYTHING)
					writer.write("\n");
			}
			timer++;
		}
		
		writer.write("TOTAL DEFRAGMENTATION OVERHEAD: " + defragCounter);
	}
	
	// method to allocate a block to a series of contiguous pages in memory
	// returns true if successfully allocated, false otherwise
	public static boolean allocate(int blockNum, boolean meToo) throws IOException {
		int blockSize = blockreq.getBlockSize(blockNum);
		
		// here, we'll do a simple first-fit algorithm to satisfy this request
		for (int i = 0; i < freeList.size(); i++) {
			Integer[] range = freeList.get(i);
			int rangeSize = range[1] - range[0] + 1;
			
			if (rangeSize >= blockSize) { // our block can fit in this gap!
				occupiedList.put(blockNum, range[0]);
				occupiedIndex.put(range[0], blockNum);
				
				if (rangeSize == blockSize) freeList.remove(i); // perfect fit :)
				else {
					Integer[] newRange = {range[0] + blockSize, range[1]};
					freeList.set(i, newRange);
				}
				
				if (OUTPUT_EVERYTHING) {
					writer.write("REQUEST SATISFIED AT INDEX " + range[0] + " AT TIME " + timer + ", WITH BLOCK " + blockNum + " OF SIZE " + blockSize + " WITH PROBABILITY " + blockreq.getReqProb(blockNum, meToo));
					if (meToo) writer.write(" USING METOO\n");
					else writer.write("\n");
				}
				return true;
			}
		}
		return false; // if we wind up here, then the fragmentation must be really bad. it means we were unable to allocate a spot.
	}
	
	// degree of fragmentation is calculated as 1 minus the proportion of the largest contiguous memory range to the total memory available
	public static double calculateFrag() {
		int totalMemory = 0;
		int largestRange = 0;
		for (int i = 0; i < freeList.size(); i++) {
			Integer[] range = freeList.get(i);
			int rangeSize = range[1] - range[0] + 1;
			totalMemory += rangeSize;
			if (rangeSize > largestRange) largestRange = rangeSize;
		}
		
		return 1 - (double)largestRange / totalMemory;
	}
	
	// method to free the specified block while updating our free list and occupied list
	public static void dealloc(int blockNum) throws IOException {
		int sIndex = occupiedList.remove(blockNum);
		int eIndex = sIndex + blockreq.getBlockSize(blockNum) - 1;
		occupiedIndex.remove(sIndex);
		
		Integer[] newRange = {-1, -1};
		
		// searching through the free list to see if we can combine this freed space with any other
		for (int i = 0; i < freeList.size(); i++) {
			Integer[] range = freeList.get(i);
			
			if (range[1] == sIndex - 1) { // merge from bottom
				newRange[0] = range[0];
				freeList.remove(i);
			} else if (range[0] == eIndex + 1) { // merge from top
				newRange[1] = range[1];
				freeList.remove(i);
			}
		}
		
		if (newRange[0] == -1) { // we haven't merged anything from the bottom
			newRange[0] = sIndex;
		}
		if (newRange[1] == -1) { // we haven't merged anything from the top
			newRange[1] = eIndex;
		}
		if (OUTPUT_EVERYTHING)
			writer.write("FREEING BLOCK " + blockNum + " AT INDEX " + sIndex + " AT TIME " + timer + "\n");
		freeList.add(newRange);
	}
	
	// defragmentation method. this one was a pain to write (and even more painful to bugfix)
	// this essentially coalesces all of the occupied spaces into a single clump, which should eliminate fragmentation
	public static void defrag() throws IOException {
		List<Integer> indexes = new ArrayList<Integer>(occupiedIndex.keySet());
		int curIndex = 0;
		
		for (int i = 0; i < indexes.size(); i++) {
			int blockNumber = occupiedIndex.get(indexes.get(i));
			int blockSize = blockreq.getBlockSize(blockNumber);
			
			occupiedList.put(blockNumber, curIndex); // updating the values here
			occupiedIndex.put(curIndex, blockNumber);
			occupiedIndex.remove(indexes.get(i)); // more clunky because you can't just update a key value in a HashMap
			
			curIndex += blockSize;
			defragCounter++; // this counter increments with every block that's moved
		}
		
		// at this point, curIndex has the tally of all of the pages that are currently occupied
		freeList.clear();
		freeList.add(new Integer[] {curIndex, NUM_PAGES});
		
		writer.write("DEFRAGMENTATION OCCURRED AT TIME " + timer + ". FREELIST NOW RANGING FROM " + freeList.get(0)[0] + " TO " + freeList.get(0)[1] + "\n");
	}
	
	// just a utility method that lets you print the free list more easily :)
	public static String fListString() {
		String s = "";
		for (int i = 0; i < freeList.size(); i++) {
			s += Arrays.toString(freeList.get(i)) + " ";
		}
		return s;
	}
}