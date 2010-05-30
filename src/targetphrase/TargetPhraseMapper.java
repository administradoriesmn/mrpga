package targetphrase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Hashtable;
import java.util.StringTokenizer;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.SequenceFile.CompressionType;
//import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapreduce.Mapper;
//import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapreduce.Reducer.Context;

public class TargetPhraseMapper extends Mapper<Object, Text, Text, IntWritable> {
	
	private Text subjectAsWord = new Text();
	private String USERNAME = "";
	private Hashtable mapParameters = new Hashtable();
	private int numElemProcessed = 0;
	private static final Log LOG = LogFactory.getLog(TargetPhraseMapper.class.getName());
	
	
	private IntWritable calculateFitness(String target, Text individual) {		
		int targetSize=target.length();
		String textAsString = individual.toString();
		int fitness=0;
		for (int j=0; j<targetSize; j++) {
			fitness += Math.abs((textAsString.toCharArray()[j] - target.charAt(j)));
		}
		return new IntWritable(fitness);
	}
	
	@Override
	protected void setup(Context cont)throws IOException {
		//LOG.info("***********DENTRO DEL SETUP DEL MAPPER**********");
		Configuration conf = cont.getConfiguration();
		FileSystem hdfs = FileSystem.get(conf);
		String users = conf.get("hadoop.job.ugi");
		String[] commas = users.split(",");
		USERNAME = commas[0];
		String HDFS_MAPPER_CONFIGURATION_FILE="/user/"+USERNAME+"/data/mapper_configuration.dat";
		Path path = new Path(HDFS_MAPPER_CONFIGURATION_FILE);
		//Validamos primero el path de entrada antes de leer del fichero
		if (!hdfs.exists(path))
		{
			throw new IOException("El fichero especificado " +HDFS_MAPPER_CONFIGURATION_FILE + " no existe");
		}
		
		if (!hdfs.isFile(path))
		{
			throw new IOException("El fichero especificado "+HDFS_MAPPER_CONFIGURATION_FILE + " no existe");
		}
		
		FSDataInputStream dis = hdfs.open(path);
		BufferedReader br = new BufferedReader(new InputStreamReader(dis));
		String strLine;
		String[]keys = {"targetPhrase","numPopulation","debugging","elitism"};
		int index=0;
		 while ((strLine = br.readLine()) != null)   {
			mapParameters.put(keys[index], strLine);
	        index++;
	      }
		 dis.close();
		 
	}
	
	
	@Override
	protected void map(Object key, Text value, Context context) throws IOException, InterruptedException 
	{
		String line = value.toString();
		int numPop = Integer.parseInt((String)mapParameters.get("numPopulation"));
		String targetPhrase = (String)mapParameters.get("targetPhrase");
		int boolElit = Integer.parseInt((String)mapParameters.get("elitism"));
		int debug = Integer.parseInt((String)mapParameters.get("debugging"));
		StringTokenizer itr = new StringTokenizer(line);
		int bestFitness = 999999; 
		Text bestIndiv = new Text();
		
		while(itr.hasMoreTokens()) {
			subjectAsWord.set(itr.nextToken());
			IntWritable elemFitness = calculateFitness(targetPhrase, subjectAsWord);
			
			//Seguimos la pista del mejor elemento...
			if (elemFitness.get() < bestFitness) {
				bestFitness = elemFitness.get();
				bestIndiv = subjectAsWord;
				}
			context.write(subjectAsWord, elemFitness);
			numElemProcessed++;
			
			if ((numElemProcessed == numPop -1)&&(boolElit==1)) {
				closeAndWrite(debug,bestIndiv,bestFitness);
			}
		}
	}
	
	/**Una vez todos los elementos hayan sido procesados, escribimos en un
	 * fichero global el mejor de ellos (si queremos introducir elitismo)...
	 */
	public void closeAndWrite(int debug,Text bestIndiv, int bestFitness) throws IOException {
		String bestDir = "/user/"+USERNAME+"/bestIndividuals";
		String bestFile = bestDir+"/bestIndiv.txt";
		Path bestDirPath = new Path(bestDir);
		Path bestIndivPath = new Path(bestFile);
		FileSystem hdfs = FileSystem.get(new Configuration());
		/**
		 * HDFS no permite que multiples mappers escriban en el mismo fichero,
		 * por lo que que creamos uno por cada mapper...
		 */
		if (hdfs.exists(bestDirPath)) {
    		//Eliminamos el directorio de los mejores individuos primero...
    		hdfs.delete(bestDirPath,true);
    	}
		FSDataOutputStream dos = hdfs.create(bestIndivPath);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(dos));
		/**
		 * Escribo el valor del individuo y su fitness, para que luego el Reducer lo lea...
		 */
		bw.write(bestIndiv.toString()+"\n");
		bw.write(bestFitness+"\n");
		bw.close();
	}	
	
}
