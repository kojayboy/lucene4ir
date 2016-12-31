package lucene4ir;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import javax.xml.bind.JAXB;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;


/**
 * Created by colin on 21/12/16.
 */

public class BigramGenerator {

    public String indexName;
    public IndexReader reader;

    public BigramGenerator() {
        System.out.println("BigramGenerator");
    /*
    Creates a file containing bigrams from the collection.
    Collection must be indexed with a shingle tokeniser.

    Assumes index has a docnum (i.e. trec doc id), title and content fields.
     */
        indexName = "";
        reader = null;

    }


    public void readBigramGeneratorParamsFromFile(String indexParamFile) {
        try {
            IndexParams p = JAXB.unmarshal(new File(indexParamFile), IndexParams.class);
            indexName = p.indexName;

        } catch (Exception e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
            System.exit(1);
        }


    }


    public void openReader() {
        try {
            reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexName)));

        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }

    }

    public void termsList(String field) throws IOException {

        // again, we'll just look at the first segment.  Terms dictionaries
        // for different segments may well be different, as they depend on
        // the individual documents that have been added.
        LeafReader leafReader = reader.leaves().get(0).reader();
        Terms terms = leafReader.terms(field);

        // The Terms object gives us some stats for this term within the segment
        System.out.println("Number of docs with this term:" + terms.getDocCount());

        TermsEnum te = terms.iterator();
        BytesRef term;
        int i = 1;
        String output="";
        while ((term = te.next()) != null) {
            System.out.println(term.utf8ToString() + " DF: " + te.docFreq() + " CF: " + te.totalTermFreq());
            if (term.utf8ToString().split(" ").length > 1) {
                output = output + i + " " + term.utf8ToString() + "\n";
                i++;
            }

        }
        Files.write(Paths.get("/Users/colin/Workspace/lucene4ir/data/ap_bigrams.qry"), output.getBytes());

    }


    public static void main(String[] args)  throws IOException {


        String statsParamFile = "";

        try {
            statsParamFile = args[0];
        } catch (Exception e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
            System.exit(1);
        }

        ExampleStatsApp statsApp = new ExampleStatsApp();

        statsApp.readExampleStatsParamsFromFile(statsParamFile);

        statsApp.openReader();
        statsApp.termsList("title");
    }
}

class BigramGeneratorParams {
    public String indexName;
}
