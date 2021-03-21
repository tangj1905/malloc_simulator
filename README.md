# malloc_simulator
An (extremely basic) malloc simulator in Java. Done in collaboration with sjw103.

This simulator models requests which are made for a block of memory of some fixed number of pages. These blocks are then allocated into main memory, represented by a contiguous series of pages, from where they can be de-allocated at a random time. Sections of free pages are tracked by the free list, and defragmentation is automatically triggered whenever it exceeds a certain threshold.

## Implementation details
In ``Requests.java``, memory blocks are stored as an array, with each element of the array storing the size of each block (in pages). There is another array of the same size which keeps track of whether each block is currently requested or not, which prevents two separate processes from requesting the same block. One consequence of this is that there can only be one instance of each block in main memory (which is definitely intended).

The probability of freeing a memory block is stored as an array, with each element of this array corresponding to the probability of freeing the ith memory block. The probability of allocating a memory block is given by a 2D array, which makes use of the "me too" feature. Since a memory block's chances of being allocated can be dependent on a previous allocated block, this 2D array is supposed to represent this conditional probability. In this particular simulator, two blocks that are near to each other are much more likely to be allocated together rather than blocks that are farther away. I also picked this probability to be based on the most recent block that was allocated, though this can be interpreted differently in other simulators. And for the regular, independent probabilities, I just put them on the main diagonal of this matrix.

The probabilities themselves are simply half-Gaussian distribution. The independent probabilities have a standard deviation of 0.001, and their mean probabilities were close to 0.0007979. For the conditional probabilities, I took a standard deviation of 0.25 and raised it to the power of the difference between the indexes of the two blocks. This made closer blocks have a greater joint probability as a result. This also gave a small, but not trivial chance of the probability exceeding 1. However, this isn't really an issue as this would just be treated as having a probability of 1, as every value produced by ``Math.random()`` falls between 0 and 1.

In ``Simulator.java``
