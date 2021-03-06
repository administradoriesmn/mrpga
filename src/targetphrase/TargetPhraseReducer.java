package targetphrase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;


/**
 * Clase que representa un nodo <code>Reducer</code> dentro del framework 
 * de ejecuci&#243;n <code>MapReduce</code>, implementando todas las funciones
 * necesarias para ello (<code>reduce()</code>, <code>setup()</code>, etc).
 * @author Alberto Luengo Cabanillas
 */
public class TargetPhraseReducer extends Reducer<Text,IntWritable,Text,IntWritable> {

	private IntWritable fitness = new IntWritable();
	private int crossSize = 2;
	private int tournamentSize = 5;
	private int numElemProcessed, numPop, boolElit, mutation, cont, numTournaments = 0;
	private static final Log LOG = LogFactory.getLog(TargetPhraseReducer.class.getName());
	
	private String[][]tournamentArray;
	private String[]tournIndiv;
	private int[]tournamentFitness = new int[tournamentSize];
	private int[]tournamentGroupFitness = new int[2*tournamentSize];
	
	//Cada posicion del array del torneo sera un Hashtable, ya que necesitamos almacenar al individuo y su fitness..
	//private Hashtable[]tournArray = new Hashtable [2*tournamentSize];
	private Hashtable[]tournArray = new Hashtable [tournamentSize];
	private String[][]crossArray = new String [tournamentSize][tournamentSize];
	private Hashtable parameters = new Hashtable();
	private Hashtable bestIndivTable = new Hashtable();
	private String USERNAME = "";
	private Random r;
	private Text bestInd = new Text("");
	private int bestIndFitness = 0;
	private double mutationRate, crossProb = 0.0;
	private String targetPhrase ="";
	private Vector bufferWinners = new Vector();
	private int indWinner = 0;

	/**
	 * M&#233;todo constructor de la clase <code>TargetPhraseReducer</code>
	 * que inicializa una nueva semilla para la generaci&#243;n de n&#250;meros aleatorios.
	 */
	TargetPhraseReducer() {
		r = new Random(System.nanoTime());
	}
	
	/**
	 * M&#233;todo <code>override</code> que se ejecutar&#225; una &#250;nica vez en el sistema
	 * que servir&#225; para leer y parsear los par&#225;metros de configuraci&#243;n necesarios
	 * para los nodos <code>Reducer</code>.
	 * @param cont Contexto en el que se ejecuta el trabajo <code>MapReduce</code>.
	 * @throws IOException Excepci&#243;n que se lanza si ha habido alg&#250;n error manipulando ficheros o directorios.
	 */
	@Override
	protected void setup(Context cont) throws IOException{
		LOG.info("***********DENTRO DEL SETUP DEL REDUCER**********");
		FileSystem hdfs = FileSystem.get(new Configuration()); 
		Configuration conf = cont.getConfiguration();
		String users = conf.get("hadoop.job.ugi");
		String[] commas = users.split(",");
		USERNAME = commas[0];
		String HDFS_REDUCER_CONFIGURATION_FILE="/user/"+USERNAME+"/data/reducer_configuration.dat";
		Path path = new Path(HDFS_REDUCER_CONFIGURATION_FILE);
		
		
		
		//Validamos primero los path de entrada antes de leer del fichero
		if (!hdfs.exists(path))
		{
			LOG.info("***********RSETUP:NO EXISTE EL FICHERO**********");
			throw new IOException("ALGUNO DE LOS FICHEROS DE CONFIGURACION NO EXISTE");	
		}
		
		if (!hdfs.isFile(path))
		{
			LOG.info("***********RSETUP: NO ES UN FICHERO VALIDO**********");
			throw new IOException("ALGUNO DE LOS FICHEROS ESPECIFICADOS NO ES VALIDO");
		}
		
		tournIndiv = new String[tournamentSize];
		tournamentArray = new String[tournamentSize][tournamentSize];
		
		FSDataInputStream dis = hdfs.open(path);
		BufferedReader br = new BufferedReader(new InputStreamReader(dis));
		String strLine;
		String[]keys = {"numPopulation","maxIterations","boolElit","mutationRate","mutation","crossProb","targetPhrase"};
		int index = 0;
		while ((strLine = br.readLine()) != null)   {
			parameters.put(keys[index], strLine);
		    index++;
		  }
		dis.close();
		numPop = Integer.parseInt((String)parameters.get("numPopulation"));
		boolElit = Integer.parseInt((String)parameters.get("boolElit"));
		mutation = Integer.parseInt((String)parameters.get("mutation"));
		mutationRate = Double.parseDouble((String)parameters.get("mutationRate"));
		crossProb = Double.parseDouble((String)parameters.get("crossProb"));
		targetPhrase = (String)parameters.get("targetPhrase");
		
		/**Si esta activada la opcion del elitismo, 
		 * escribimos el mejor elemento en la salida...
		 */
		if (boolElit == 1) {
			String BEST_INDIVIDUAL_FILE="/user/"+USERNAME+"/bestIndividuals/bestIndiv.dat";
			Path bestIndPath = new Path(BEST_INDIVIDUAL_FILE);
			
			int index2=0;
			FSDataInputStream dis2 = hdfs.open(bestIndPath);
			BufferedReader br2 = new BufferedReader(new InputStreamReader(dis2));
			String[]bestIndKeys = {"bestIndiv","bestFitness"};
			while ((strLine = br2.readLine()) != null)   {
				bestIndivTable.put(bestIndKeys[index2], strLine);
			    index2++;
			  }
			dis2.close();

			bestInd = new Text((String)bestIndivTable.get("bestIndiv"));
			bestIndFitness = Integer.parseInt((String)bestIndivTable.get("bestFitness"));
			try {
				cont.write(bestInd, new IntWritable(bestIndFitness));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}	
		}
	}
	
	/**
	 * MM&#233;todo <code>override</code> que recibe los distintos pares (clave,valor) 
	 * de alg&#250;n nodo <code>Mapper</code> y los procesa de acuerdo a una serie de reglas, devolviendo una lista de
	 * pares (clave,valor) al contexto.
	 * @param key La clave del par (clave,valor) que genera este m&#233;todo.
	 * @param values El conjunto de todos los valores fitness de los individuos..
	 * @param context Contexto en el que se ejecuta el trabajo <code>MapReduce</code>.
	 * @throws IOException Excepci&#243;n que se lanza si ha habido alg&#250;n error manipulando ficheros o directorios.
	 * @throws InterruptedException Excepción propia de <code>Hadoop</code> que se lanza si se interrumpe alguna transacci&#243;n at&#243;mica.
	 */
	@Override
	protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException 
	{
		
		Iterator<IntWritable> valuesIter =values.iterator();
		
		while (valuesIter.hasNext()) 
		{
			fitness = valuesIter.next();
			//LOG.info("EL INDIVIDUO "+numElemProcessed+" TIENE CLAVE "+key.toString());
			
//			tournIndiv[numElemProcessed%tournamentSize + tournamentSize] = key.toString();
//			tournamentFitness[numElemProcessed%tournamentSize + tournamentSize] = fitness.get();
			
			tournIndiv[numElemProcessed%tournamentSize] = key.toString();
			tournamentFitness[numElemProcessed%tournamentSize] = fitness.get();
			
			//Cada <tournamentSize> iteraciones, meto los arrays en las posiciones del array de arrays...
			//Esperamos que se unan los participantes del torneo...
			if ((numElemProcessed % (tournamentSize))==0 && numElemProcessed!=0 && cont!=tournamentSize &&numTournaments==0)
			{
				//Calculamos el mejor fitness de los elementos del grupo...
				LOG.info("*****CALCULAMOS LOS VALORES GRUPALES******");
				int bestGroupFitness = 99999;
				for (int i=0; i < tournamentFitness.length;i++)
				{
					if (tournamentFitness[i] < bestGroupFitness)
						bestGroupFitness = tournamentFitness[i];
					//LOG.info("TOURNFIT["+i+"] VALE "+tournamentFitness[i]);
					//LOG.info("BESTGROUPFITNESS VALE "+bestGroupFitness);
				}	
				//int currentPos = (numElemProcessed%tournamentSize) + cont;
				//tournamentArray[cont] = tournIndiv;
				//OJO!PROBLEMA EN LA ASIGNACION DIRECTA DE ARRIBA!
				for (int i = 0; i < tournIndiv.length; i++) {
					tournamentArray[cont][i] = tournIndiv[i]; 
				}
				
				for (int a = 0; a<tournArray.length;a++) {
					String[]stringArr = tournamentArray[a];
					for (int b = 0; b<stringArr.length;b++) {
						//LOG.info("LOS ELEMENTOS DEL TOURNARRAY "+b+" VALE "+stringArr[b]);
					}
				}
				//tournamentGroupFitness[numElemProcessed%tournamentSize + cont] = bestGroupFitness;
				tournamentGroupFitness[cont] = bestGroupFitness;
				
				//LOG.info("TOURNAMENTGROUPFITNESS["+cont+"] METO "+tournamentGroupFitness[cont]);
				cont++;
				//LOG.info("CONT VALE "+cont);
			}
			//Cuando tengamos [tournamentSize] elementos en el array de arrays celebramos el torneo...
			if ((cont ==tournamentSize) && (numTournaments == 0))
			{
				//LOG.info("*****CELEBRO EL PRIMER TORNEO******");
//				for (int a = 0; a<tournArray.length;a++) {
//					String[]stringArr = tournamentArray[a];
//					for (int b = 0; b<stringArr.length;b++) {
//						//LOG.info("LOS ELEMENTOS DEL TOURNARRAY "+b+" VALE "+stringArr[b]);
//					}
//				}
//				for (int b = 0; b<tournamentGroupFitness.length;b++) {
//					//LOG.info("LOS ELEMENTOS DE TOURNAMENTGROUPFITNESS "+b+" VALE "+tournamentGroupFitness[b]);
//					}
				selectionAndCrossover(numElemProcessed, tournamentArray, context);
				numTournaments++;
				numElemProcessed++;
				continue;
			}
			
			if (numTournaments!=0) 
			{
				//LOG.info("*****CELEBRO EL RESTO DE TORNEOS******");
				//Si no es el primer torneo que celebramos, solo esperamos
				//por el siguiente participante (5 elementos más) y sobreescribimos...
				int bestGroupFitness = 99999;
				for (int i = 0; i < tournIndiv.length; i++) {
					tournamentArray[indWinner][i] = tournIndiv[i];
					if (tournamentFitness[i] < bestGroupFitness)
						bestGroupFitness = tournamentFitness[i];
				}
				tournamentGroupFitness[indWinner] = bestGroupFitness;
						
				selectionAndCrossover(numElemProcessed, tournamentArray, context);
				numTournaments++;
			}
			numElemProcessed++;
		}
		//Si todos los elementos han sido procesados...
		if(numElemProcessed == numPop) {
			closeAndWrite(context);
		}	
	}
	
	/**
	 * M&#233;todo que ejecuta el &#250;	ltimo torneo de individuos cuando todos los usuarios
	 * han sido procesados. Esto se debe a la ventana activa de <code>tournamentSize</code>
	 * elementos con los que se trabaja
	 * @param context Instancia de la clase <code>Context</code> que facilita informaci&#243;n acerca
	 * del trabajo <code>MapReduce</code> que se est&#225; ejecutando. 
	 * @throws IOException Excepci&#243;n lanzada al haber alg&#250;n problema manipulando
	 * ficheros o directorios.
	 * @throws InterruptedException Excepci&#243;n propia del API de Hadoop que se lanza si hay alg&#250;	n
	 * problema escribiendo los individuos procesados.
	 * @throws IOException Excepci&#243;n que se lanza si ha habido alg&#250;n error manipulando ficheros o directorios.
	 * @throws InterruptedException Excepción propia de <code>Hadoop</code> que se lanza si se interrumpe alguna transacci&#243;n at&#243;mica.
	 */
	public void closeAndWrite(Context context) throws IOException, InterruptedException {
		LOG.info("*****TODOS LOS ELEMENTOS HAN SIDO PROCESADOS******");
		//Acabamos con la ultima ventana del torneo...
		for (int lastIter=0; lastIter <tournamentSize;lastIter++)
		{
			int bestGroupFitness = 99999;
			for (int i = 0; i < tournIndiv.length; i++) {
				tournamentArray[indWinner][i] = tournIndiv[i];
				if (tournamentFitness[i] < bestGroupFitness)
					bestGroupFitness = tournamentFitness[i];
			}
			tournamentGroupFitness[indWinner] = bestGroupFitness;
			selectionAndCrossover(numElemProcessed, tournamentArray, context);
			numElemProcessed += lastIter;
		}
		
	}
	
	/**
	 * M&#233;todo que se encarga de escribir los distintos resultados sub-&#243;ptimos en
	 * el fichero de salida correspondiente, tras haber realizado una selecci&#243;n
	 * de los mejores individuos por medio de un torneo, cruzarlos y, si se ha
	 * indicado, mutarlos.
	 * @param numElemProcessed N&#250;mero de individuos procesados de una poblaci&#243;n.
	 * @param tournArray Conjunto de individuos que participar&#225;	n en el torneo.
	 * @param context Instancia de la clase <code>Context</code> que facilita informaci&#243;n acerca
	 * del trabajo <code>MapReduce</code> que se est&#225;	 ejecutando.
	 */
	private void selectionAndCrossover(int numElemProcessed, String[][]tournArray,Context context){
		String[] tournWinner = this.tournSelection(tournArray);
		String[][] newIndividuals = null;
		crossArray[numElemProcessed % crossSize] = tournWinner; 
		//LOG.info("DENTRO DE SELECTIONANDCROSSOVER EL GANADOR DEL TORNEO ES " +tournWinner);
		//LOG.info("EL TAMANHO DE LA POBLACION ES "+numPop);
		if (((numElemProcessed - tournamentSize) % crossSize) == (crossSize - 1) && (bufferWinners.size() <= numPop)) 
		{
			if (crossProb < r.nextDouble()) 
				newIndividuals = crossOver();
			else
				newIndividuals = crossArray;
			try 
			{
			  for(int i=0;i < crossSize;i++)
			  {	  
				//LOG.info("******ESCRITURA EN SELECTIONANDCROSSOVER*****");
				String[] individuals = newIndividuals[i];
				for (int j=0;j<individuals.length;j++)
				{
					/**
					 * Tengo que mirar si el individuo ya existe en el "buffer";
					 * es decir, si salio como ganador de un torneo una vez y
					 * vuelve a salir, no se escribe...
					 */
					if (bufferWinners.contains(individuals[j]))
						continue;
					//Escribimos en el fichero y en el buffer de ganadores...
					LOG.info("DENTRO DE SELECTIONANDCROSSOVER EL VALOR["+j+"]QUE ESCRIBO ES " +individuals[j]);
					Text indiv = new Text(individuals[j]);
					/**Para no caer en mesetas o maximos locales, vamos a mutar a los
					 * individuos antes de cruzarlos...
					 */
					if (mutation == 1)
						context.write(this.mutate(indiv), fitness);
					else
						context.write(indiv, fitness);
					//Escribo en el buffer de ganadores...
					bufferWinners.addElement(individuals[j]);
				}
			  }
			}
			catch(ArrayIndexOutOfBoundsException aioobe) {
				aioobe.printStackTrace();
			} 
			catch (IOException e) {
				e.printStackTrace();
			} 
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			catch (NullPointerException e) {
			}
			
		}	
	}
	
	/**
	 * M&#233;todo que implementa el metodo de seleccion por torneo sin reemplazamiento
	 * entre los distintos individuos de una poblaci&#243;n, devolviendo los ganadores
	 * del mismo.
	 * @param tournArray Conjunto de individuos (contendientes) sobre los que realizar el torneo.
	 * @return Conjunto de individuos ganadores del torneo.
	 */
	private String[] tournSelection(String[][]tournArray) {
		/**Dentro del array de arrays de contendientes, elegimos al que tenga mejor fitness para
		 * luego cruzarlo... 
		 */
		String[] tournWinner = null;
		long bestFitness = 999999;
		Vector indWinners = new Vector();
		
		for (int i=0;i <tournamentSize ;i++) {
			//LOG.info("TOURNAMENTGROUPFITNESS["+i+"] VALE "+tournamentGroupFitness[i]);
			if (tournamentGroupFitness[i] < bestFitness)
			{
				bestFitness = tournamentGroupFitness[i];
				//LOG.info("DENTRO DE TOURNSELECTION, LOS FITNESS VALEN "+bestFitness);
				tournWinner = tournArray[i];
				indWinners.addElement(i);
			}
		}
		//LOG.info("EL MEJOR FITNESS DENTRO DEL TOURNSELECTION ES "+bestFitness);
		for (int aux = 0; aux<tournWinner.length;aux++) {
			//LOG.info("TOURNWINNER "+aux+" VALE "+tournWinner[aux]);
		}
		//Sacamos el indice del elemento ganador para escribir el siguiente
		//elemento en él...
		indWinner = Integer.parseInt(indWinners.elementAt((indWinners.size()-1)).toString());
		
		return tournWinner;
	}

	
	
	/**
	 * M&#233;todo que realiza la operaci&#243;n de cruce sobre dos grupos de individuos.
	 * @return Los grupos de individuos cruzados aleatoriamente.
	 */
	private String[][] crossOver() {
		LOG.info("*********EN EL CROSSOVER**********");
		String[][] newIndividuals = new String[crossArray.length][tournamentSize];
		
		String[] parent1 = crossArray[0];
		String[] parent2 = crossArray[1];
		
		
//		LOG.info("PARENT1 LEN ES: "+parent1.length);
//		LOG.info("PARENT2 LEN ES: "+parent2.length);
		
		//Establecemos el punto de corte para ver como se generan los descendientes
		//int cutPoint = (int) ((Math.random()*(parent1.length- 1))+ 1);
		int cutPoint = (int) ((Math.random()*(parent1[0].length()- 1))+ 1);
		//LOG.info("EL PUNTO DE CORTE EN EL CROSSOVER ES: "+cutPoint);
		
		
		//Creamos las partes identicas a las de los padres...
		String[] child1 = new String[parent1.length];
		String[] child2 = new String[parent2.length];
		
		for (int aux = 0; aux<parent1.length;aux++) {
			//LOG.info("PARENT1["+aux+"] VALE "+parent1[aux]);
			//LOG.info("PARENT2["+aux+"] VALE "+parent2[aux]);
			String p1 = parent1[aux];
			String p2 = parent2[aux];
			String child1P1 = p1.substring(0, cutPoint);
			String child1P2 = p2.substring(cutPoint, (parent1[aux].length()));
			String child2P1 = p2.substring(0, cutPoint);
			String child2P2 = p1.substring(cutPoint, (parent1[aux].length()));
			
			//Concatenamos...
			child1[aux] = child1P1+child1P2;
			//LOG.info("CHILD1["+aux+"] VALE "+child1[aux]);
			child2[aux] = child2P1+child2P2;
			//LOG.info("CHILD2["+aux+"] VALE "+child2[aux]);
		}
				
		//Creamos un nuevo array de arrays...
		newIndividuals[0] = child1;
		newIndividuals[1] = child2;
		
		return newIndividuals;
	}
	
	
	/**
	 * M&#233;todo que implementa la operaci&#243;n de mutaci&#243;n sobre un individuo concreto,
	 * en funci&#243;n de una probabilidad.
	 * @param Individuo a mutar.
	 * @return Individuo mutado.
	 */
	private Text mutate(Text individual)
	{
		double random = r.nextDouble();
		String sText = individual.toString();
		String mutInd = "";
		int beginIndex = 0, endIndex = 0;
		
		//Si el numero aleatorio cae dentro del rango de mutacion, seguimos...
		if (random < mutationRate) {
			//LOG.info("**MUTAMOS AL INDIVIDUO "+individual+" *****");
			//Obtenemos dos posiciones aleatorias dentro del Individuo...
			int r1 = (int) ((Math.random()*(sText.length()- 1))+ 1);
			int r2 = (int) ((Math.random()*(sText.length()- 1))+ 1);
			
			if (r1 == r2) {
				mutInd = sText;
			}
			else {
				if (r1 < r2) {
					beginIndex = r1;
					endIndex = r2;
				}
				else {
					beginIndex = r2;
					endIndex = r1;
				}
				//Obtenemos los genes que se encuentran en esas posiciones...
				char g1 = sText.charAt(r1);
				char g2 = sText.charAt(r2);
				
				//Intercambiamos las posiciones de esos genes...
				mutInd = sText.substring(0,beginIndex);
				mutInd = mutInd.concat(g2+"").concat(sText.substring(beginIndex+1,endIndex)).concat(g1+"").concat(sText.substring(endIndex+1, sText.length()));
				//LOG.info("****** EL INDIVIDUO MUTADO ES "+mutInd+" *****");
			}
		}
		//...si no, devolvemos el individuo tal cual...
		else {
			mutInd = sText;
		}
		return new Text(mutInd);
	}
}
