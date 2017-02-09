#!/bin/bash

params=(1 2 3 4 5 6 7 8 16 32 64 128)
for i in "${params[@]}"; do
    python lucene_models_trec_script_generator.py apIndex ap_bigram.qry ap PL2 $i
done