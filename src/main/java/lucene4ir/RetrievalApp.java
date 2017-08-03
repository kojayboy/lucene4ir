package lucene4ir;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.search.similarities.LMSimilarity.CollectionModel;
import org.apache.lucene.store.FSDirectory;
import lucene4ir.similarity.SMARTBNNBNNSimilarity;
import lucene4ir.similarity.OKAPIBM25Similarity;
import lucene4ir.similarity.BM25Similarity;
import lucene4ir.utils.TokenAnalyzerMaker;
import org.w3c.dom.NodeList;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
/**
 * Created by leif on 22/08/2016.
 * Edited by kojayboy on 01/07/2017
 */
public class RetrievalApp {

    public RetrievalParams p;
    public Field fp;
    public String fieldsFile;

    private boolean fielded;
    protected Similarity simfn;
    private IndexReader reader;
    private IndexSearcher searcher;
    protected Analyzer analyzer;
    protected QueryParser parser;
    private CollectionModel colModel;

    private enum SimModel {
        DEF, BM25, BM25L, LMD, LMJ, PL2, TFIDF,
	OKAPIBM25, SMARTBNNBNN
    }

    public SimModel sim;

    private void setSim(String val){
        try {
            sim = SimModel.valueOf(p.model.toUpperCase());
        } catch (Exception e){
            System.out.println("Similarity Function Not Recognized - Setting to Default");
            System.out.println("Possible Similarity Functions are:");
            for(SimModel value: SimModel.values()){
                System.out.println("<model>"+value.name()+"</model>");
            }
            sim = SimModel.DEF;
        }
    }

    public void selectSimilarityFunction(SimModel sim){
        colModel = null;
        switch(sim){
            case OKAPIBM25:
                System.out.println("OKAPI BM25 Similarity Function");
                simfn = new OKAPIBM25Similarity(1.2f, 0.75f);
                break;
            case SMARTBNNBNN:
                System.out.println("SMART bnn.bnn Similarity Function");
                simfn = new SMARTBNNBNNSimilarity();
            case BM25:
                System.out.println("BM25 Similarity Function");
                simfn = new BM25Similarity(p.k, p.b);
                break;
            case LMD:
                System.out.println("LM Dirichlet Similarity Function");
                colModel = new LMSimilarity.DefaultCollectionModel();
                simfn = new LMDirichletSimilarity(colModel, p.mu);
                break;
            case LMJ:
                System.out.println("LM Jelinek Mercer Similarity Function");
                colModel = new LMSimilarity.DefaultCollectionModel();
                simfn = new LMJelinekMercerSimilarity(colModel, p.lam);
                break;
            case PL2:
                System.out.println("PL2 Similarity Function (?)");
                BasicModel bm = new BasicModelP();
                AfterEffect ae = new AfterEffectL();
                Normalization nn = new NormalizationH2(p.c);
                simfn = new DFRSimilarity(bm, ae, nn);
                break;
            default:
                System.out.println("Default Similarity Function");
                simfn = new BM25Similarity();
                break;
        }
    }

    public void readParamsFromFile(String paramFile) {

        p = new RetrievalParams();
        fielded = false;

        try {
            p = JAXB.unmarshal(new File(paramFile), RetrievalParams.class);
        } catch (Exception e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
            System.exit(1);
        }

        setSim(p.model);

        if (p.maxResults == 0.0) {
            p.maxResults = 1000;
        }
        if (p.b == 0.0) {
            p.b = 0.75f;
        }
        if (p.beta == 0.0) {
            p.beta = 500f;
        }
        if (p.k == 0.0) {
            p.k = 1.2f;
        }
        if (p.delta == 0.0) {
            p.delta = 1.0f;
        }
        if (p.lam == 0.0) {
            p.lam = 0.5f;
        }
        if (p.mu == 0.0) {
            p.mu = 500f;
        }
        if (p.c == 0.0) {
            p.c = 10.0f;
        }
        if (p.model == null) {
            p.model = "def";
        }
        if (p.runTag == null) {
            p.runTag = p.model.toLowerCase();
        }

        if (p.resultFile == null) {
            p.resultFile = p.runTag + "_results.res";
        }

        fieldsFile = p.fieldsFile;

        System.out.println("Path to index: " + p.indexName);
        System.out.println("Query File: " + p.queryFile);
        System.out.println("Result File: " + p.resultFile);
        System.out.println("Model: " + p.model);
        System.out.println("Max Results: " + p.maxResults);
        System.out.println("b: " + p.b);
        System.out.println("Path to fieldsFile: " + p.fieldsFile);

        if (p.tokenFilterFile != null) {
            TokenAnalyzerMaker tam = new TokenAnalyzerMaker();
            analyzer = tam.createAnalyzer(p.tokenFilterFile);
        } else {
            analyzer = LuceneConstants.ANALYZER;
        }

        if (p.tokenFilterFile != null) {
            TokenAnalyzerMaker tam = new TokenAnalyzerMaker();
            analyzer = tam.createAnalyzer(p.tokenFilterFile);
        } else {
            analyzer = LuceneConstants.ANALYZER;
        }

        setSim(p.model);
    }

    public void processQueryFile(){
        /*
        Assumes the query file contains a qno followed by the query terms.
        One query per line. i.e.

        Q1 hello world
        Q2 hello hello
        Q3 hello etc
         */
        try {
            BufferedReader br = new BufferedReader(new FileReader(p.queryFile));
            File file = new File(p.resultFile);
            FileWriter fw = new FileWriter(file);

            try {
                String line = br.readLine();
                while (line != null){

                    String[] parts = line.split("\\s");
                    String qno = parts[0].replaceAll("\\s", "");;
                    String queryTerms = "";
                    for (int i=1; i<parts.length; i++) {
                        queryTerms = queryTerms + parts[i].replaceAll("\\s", "") + " ";
                    }
                    queryTerms = queryTerms.trim();
                    ScoreDoc[] scored = runQuery(qno, queryTerms);

                    int n = Math.min(p.maxResults, scored.length);

                    for(int i=0; i<n; i++){
                        Document doc = searcher.doc(scored[i].doc);
                        String docno = doc.get("docnum");
                        fw.write(qno + " QO " + docno + " " + (i+1) + " " + scored[i].score + " " + p.runTag);
                        fw.write(System.lineSeparator());
                    }

                    line = br.readLine();
                }

            } finally {
                br.close();
                fw.close();
            }
        } catch (Exception e){
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }
    }

    public ScoreDoc[] runQuery(String qno, String queryTerms) {
        ScoreDoc[] hits = null;

        try {
            System.out.println("Query No.: " + qno + " " + queryTerms);
            Query query = parser.parse(QueryParser.escape(queryTerms));
            TopDocs results = searcher.search(query, p.maxResults);
            hits = results.scoreDocs;
        } catch (IOException | ParseException ex) {
            System.out.println(" caught a " + ex.getClass() +
                    "\n with message: " + ex.getMessage());
        }
        return hits;
    }

    public RetrievalApp(String retrievalParamFile){
        System.out.println("Retrieval App");
        readParamsFromFile(retrievalParamFile);
        try {
            reader = DirectoryReader.open(FSDirectory.open( new File(p.indexName).toPath()) );
            searcher = new IndexSearcher(reader);

            // create similarity function and parameter
            selectSimilarityFunction(sim);
            searcher.setSimilarity(simfn);
            parser = new QueryParser("all",analyzer);


        } catch (Exception e){
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }
    }

    public static void main(String []args) {


        String retrievalParamFile = "";

        try {
            retrievalParamFile = args[0];
        } catch (Exception e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
            System.exit(1);
        }

        RetrievalApp retriever = new RetrievalApp(retrievalParamFile);
        retriever.processQueryFile();
    }

}

@XmlRootElement(name = "RetrievalParams")
@XmlAccessorType(XmlAccessType.FIELD)
class RetrievalParams {
    public String indexName;
    public String queryFile;
    public String resultFile;
    public String model;
    public int maxResults;
    public float k;
    public float b;
    public float lam;
    public float beta;
    public float mu;
    public float c;
    public float delta;
    public String runTag;
    public String tokenFilterFile;
    public String fieldsFile;
}