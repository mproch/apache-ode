(1..8).each { j -> 
i=String.format("%03d", Integer.parseInt(Integer.toBinaryString(j-1)))
println """\
${j}nmr.service={http://sample.bpel.org/bpel/sample}OnEventCorrelation$i
${j}nmr.operation=opInOut
${j}request=<msg><payload>abc3-$i</payload><data>t3-$i</data></msg>
${j}response=.*result-r1tIn1tIn2tInOut3tInOut4.*\
"""

}

