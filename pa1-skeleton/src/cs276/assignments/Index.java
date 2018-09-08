package cs276.assignments;

import cs276.util.Pair;
import cs276.util.TermDocComparator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.LinkedList;
import java.util.List;

public class Index {

	// Term id -> (position in index file, doc frequency) dictionary
	private static Map<Integer, Pair<Long, Integer>> postingDict 
		= new TreeMap<Integer, Pair<Long, Integer>>();
	// Doc name -> doc id dictionary
	private static Map<String, Integer> docDict
		= new TreeMap<String, Integer>();
	// Term -> term id dictionary
	private static Map<String, Integer> termDict
		= new TreeMap<String, Integer>();
	// Block queue
	private static LinkedList<File> blockQueue
		= new LinkedList<File>();

	// Total file counter
	private static int totalFileCount = 0;
	// Document counter
	private static int docIdCounter = 0;
	// Term counter
	private static int wordIdCounter = 0;
	// Index
	private static BaseIndex index = null;

	
	/* 
	 * Write a posting list to the given file 
	 * You should record the file position of this posting list
	 * so that you can read it back during retrieval
	 * 
	 * */
	private static void writePosting(FileChannel fc, PostingList posting)
			throws IOException {
		
		
		//update postingDict
		if (blockQueue.isEmpty()) {
			postingDict.put(posting.getTermId(), new Pair<>(fc.position(),posting.getList().size()));
		}
		//writing posting to FileChannel
		try {
			index.writePosting(fc, posting);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	
	public static Integer popNextOrNull(Iterator<Integer> iterator) {
		if(iterator.hasNext()) {
			return iterator.next();
		}
		else {
		return null;
		}
		
	}
	
	//merging
	
	public static PostingList mergePostings(PostingList p1, PostingList p2) {
		Iterator<Integer> iterator1 = p1.getList().iterator();
        Iterator<Integer> iterator2 = p2.getList().iterator();
        List<Integer> postings = new ArrayList<Integer>();
        Integer docId1 = popNextOrNull(iterator1);
        Integer docId2 = popNextOrNull(iterator2);
        Integer prevDocId = 0;
        while (docId1 != null && docId2 != null) {
            if (docId1.compareTo(docId2) < 0) {
                if (prevDocId.compareTo(docId1) < 0) {
                    postings.add(docId1);
                    prevDocId = docId1;
                }
                docId1 = popNextOrNull(iterator1);
            } else {
                if (prevDocId.compareTo(docId2) < 0) {
                    postings.add(docId2);
                    prevDocId = docId2;
                }
                docId2 = popNextOrNull(iterator2);
            }
        }

        while (docId1 != null) {
            if (prevDocId.compareTo(docId1) < 0) {
                postings.add(docId1);
            }
            docId1 = popNextOrNull(iterator1);
        }

        while (docId2 != null) {
            if (prevDocId.compareTo(docId2) < 0) {
                postings.add(docId2);
            }
            docId2 = popNextOrNull(iterator2);
        }

        return new PostingList(p1.getTermId(), postings);
		
	}
	////
	public static void main(String[] args) throws Throwable {
		/* Parse command line */
		if (args.length != 3) {
			System.err
					.println("Usage: java Index [Basic|VB|Gamma] data_dir output_dir");
			return;
		}

		/* Get index */
		String className = "cs276.assignments." + args[0] + "Index";
		try {
			Class<?> indexClass = Class.forName(className);
			index = (BaseIndex) indexClass.newInstance();
		} catch (Exception e) {
			System.err
					.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}

		/* Get root directory */
		String root = args[1];
		File rootdir = new File(root);
		if (!rootdir.exists() || !rootdir.isDirectory()) {
			System.err.println("Invalid data directory: " + root);
			return;
		}

		/* Get output directory */
		String output = args[2];
		File outdir = new File(output);
		if (outdir.exists() && !outdir.isDirectory()) {
			System.err.println("Invalid output directory: " + output);
			return;
		}

		if (!outdir.exists()) {
			if (!outdir.mkdirs()) {
				System.err.println("Create output directory failure");
				return;
			}
		}

		/* A filter to get rid of all files starting with .*/
		FileFilter filter = new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				String name = pathname.getName();
				return !name.startsWith(".");
			}
		};

		/* BSBI indexing algorithm */
		File[] dirlist = rootdir.listFiles(filter);

		
		/* For each block */
		for (File block : dirlist) {
			File blockFile = new File(output, block.getName());
			blockQueue.add(blockFile);

			File blockDir = new File(root, block.getName());
			File[] filelist = blockDir.listFiles(filter);
			
			/* For each file */
			for (File file : filelist) {
				++totalFileCount;
				String fileName = block.getName() + "/" + file.getName();
				int docId=++docIdCounter;
				docDict.put(fileName, docId);
				
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				while ((line = reader.readLine()) != null) {
					String[] tokens = line.trim().split("\\s+");
					for (String token : tokens) {
						
						int termId;
						//if token is already there
						if (!termDict.containsKey(token)) {
							termId = ++wordIdCounter;
							termDict.put(token, termId);
						}
						//insert token and get new termId
						else
						{
							termId=termDict.get(token);
						}
						
						//array
						List<Pair<Integer, Integer>> pairs = new ArrayList<Pair<Integer, Integer>>();
						//adding termId, docId into pairs
						pairs.add(new Pair(termId,docId));
						
					}
				}
				reader.close();
			}

			/* Sort and output */
			if (!blockFile.createNewFile()) {
				System.err.println("Create new block failure.");
				return;
			}
			
			RandomAccessFile bfc = new RandomAccessFile(blockFile, "rw");
			List<Pair<Integer, Integer>> pairs = new ArrayList<Pair<Integer, Integer>>();
			  // sort pairs
            Collections.sort(pairs, new TermDocComparator());

            // output
            int cnt = 0, prevTermId = -1, termId, prevDocId = -1, docId;
            if (pairs.size() > 0)
                // setting a valid prevTermID
                prevTermId = pairs.get(0).getFirst();

            List<Integer> postings = new ArrayList<Integer>();
            for (Pair<Integer, Integer> p : pairs) {
                termId = p.getFirst();
                docId = p.getSecond();

                if (termId == prevTermId) {
                    // duplicates only once
                    if (prevDocId != docId) {
                        postings.add(docId);
                    }
                    prevDocId = docId;
                } else {
                    // if a different term is encountered write postings of previous term to disk
                    writePosting(bfc.getChannel(), new PostingList(prevTermId, postings));

                    // start new postings
                    postings.clear();
                    postings.add(docId);
                    prevTermId = termId;
                    prevDocId = docId;
                }
            }
            writePosting(bfc.getChannel(), new PostingList(prevTermId, postings));

            // clear the contents in pairs 
            pairs.clear();
			//close the fileChannel
			bfc.close();
		}

		/* Required: output total number of files. */
		System.out.println(totalFileCount);

		/* Merge blocks */
		while (true) {
			if (blockQueue.size() <= 1)
				break;

			File b1 = blockQueue.removeFirst();
			File b2 = blockQueue.removeFirst();
			
			File combfile = new File(output, b1.getName() + "+" + b2.getName());
			if (!combfile.createNewFile()) {
				System.err.println("Create new block failure.");
				return;
			}

			RandomAccessFile bf1 = new RandomAccessFile(b1, "r");
			RandomAccessFile bf2 = new RandomAccessFile(b2, "r");
			RandomAccessFile mf = new RandomAccessFile(combfile, "rw");
			 /*
             * merge two PostingList
             */
			
			
            FileChannel fc1 = bf1.getChannel();
            FileChannel fc2 = bf2.getChannel();
            FileChannel mfc = mf.getChannel();

            PostingList p1 = index.readPosting(fc1);
            PostingList p2 = index.readPosting(fc2);

            while (p1 != null && p2 != null) {
                int t1 = p1.getTermId();
                int t2 = p2.getTermId();

                if (t1 == t2) {
                    // merge postings of the same term
                    PostingList p3 = mergePostings(p1, p2);

                    // write p3 to disk
                    writePosting(mfc, p3);
                    p1 = index.readPosting(fc1);
                    p2 = index.readPosting(fc2);
                } else if (t1 < t2) {
                    // write p1
                    writePosting(mfc, p1);
                    p1 = index.readPosting(fc1);
                } else {
                    // write p2
                    writePosting(mfc, p2);
                    p2 = index.readPosting(fc2);
                }
            }

            while (p1 != null) {
                writePosting(mfc, p1);
                p1 = index.readPosting(fc1);
            }

            while (p2 != null) {
                writePosting(mfc, p2);
                p2 = index.readPosting(fc2);
            }
			bf1.close();
			bf2.close();
			mf.close();
			b1.delete();
			b2.delete();
			blockQueue.add(combfile);
		}

		/* Dump constructed index back into file system */
		File indexFile = blockQueue.removeFirst();
		indexFile.renameTo(new File(output, "corpus.index"));

		BufferedWriter termWriter = new BufferedWriter(new FileWriter(new File(
				output, "term.dict")));
		for (String term : termDict.keySet()) {
			termWriter.write(term + "\t" + termDict.get(term) + "\n");
		}
		termWriter.close();

		BufferedWriter docWriter = new BufferedWriter(new FileWriter(new File(
				output, "doc.dict")));
		for (String doc : docDict.keySet()) {
			docWriter.write(doc + "\t" + docDict.get(doc) + "\n");
		}
		docWriter.close();

		BufferedWriter postWriter = new BufferedWriter(new FileWriter(new File(
				output, "posting.dict")));
		for (Integer termId : postingDict.keySet()) {
			postWriter.write(termId + "\t" + postingDict.get(termId).getFirst()
					+ "\t" + postingDict.get(termId).getSecond() + "\n");
		}
		postWriter.close();
	}

}
