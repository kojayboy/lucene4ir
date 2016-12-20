package lucene4ir.utils;


/**
 * Created by colin on 05/12/16.
 */

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.*;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

public final class TokenizerUtil {

    public Analyzer analyzer;

    public TokenizerUtil(String tokenFilterFile) throws IOException {
        TokenFilters tokenFilters = JAXB.unmarshal(new File(tokenFilterFile), TokenFilters.class);
        CustomAnalyzer.Builder builder;
        if (tokenFilters.getResourceDir() != null) {
            builder = CustomAnalyzer.builder(Paths.get(tokenFilters.getResourceDir()));
        }
        else {
            builder = CustomAnalyzer.builder();
        }
        builder.withTokenizer(tokenFilters.getTokenizer());
        for(TokenFilter filter : tokenFilters.getTokenFilters())  {
            System.out.println("Token filter: " + filter.getName());
            List<Param> params = filter.getParams();
            if (params.size() > 0) {
                Map<String, String> paramMap = new HashMap<>();
                for (Param param : params) {
                    paramMap.put(param.getKey(), param.getValue());
                }
                builder.addTokenFilter(filter.getName(), paramMap);
            } else {
                builder.addTokenFilter(filter.getName());
            }
        }
        analyzer = builder.build();
    }

    public List<String> tokenizeString(Analyzer analyzer, String string) {
        List<String> result = new ArrayList<String>();
        try {
            TokenStream stream  = analyzer.tokenStream(null, new StringReader(string));
            stream.reset();
            while (stream.incrementToken()) {
                result.add(stream.getAttribute(CharTermAttribute.class).toString());
            }
            stream.close();
        } catch (IOException e) {
            // not thrown b/c we're using a string reader...
            throw new RuntimeException(e);
        }
        return result;
    }

    public Analyzer getAnalyzer(){
        return this.analyzer;
    }
}


@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "tokenFilters")
class TokenFilters {
    private String tokenizer;

    private String resourceDir;

    @XmlElement(name = "tokenFilter", type = TokenFilter.class)
    private List<TokenFilter> tokenFilters = new ArrayList<>();

    public TokenFilters(){}

    public TokenFilters(String tokenizer, List<TokenFilter> tokenFilters) {
        this.tokenizer = tokenizer;
        this.tokenFilters = tokenFilters;
    }

    public String getTokenizer() {
        return tokenizer;
    }

    public void setTokenizer(String tokenizer) {
        this.tokenizer = tokenizer;
    }

    public String getResourceDir() {
        return resourceDir;
    }

    public void setResourceDir(String resourceDir) {
        this.resourceDir = resourceDir;
    }

    public List<TokenFilter> getTokenFilters() {
        return tokenFilters;
    }

    public void setTokenFilters(List<TokenFilter> tokenFilters) {
        this.tokenFilters = tokenFilters;
    }
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "tokenFilter")
class TokenFilter {
    private String name;

    @XmlElement(name = "param", type = Param.class)
    private List<Param> params = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Param> getParams() {
        return params;
    }

    public void setParams(List<Param> params) {
        this.params = params;
    }
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "param")
class Param {
    private String key;
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}