package fuentes;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.Scanner;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.pig.PigServer;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.impl.PigContext;


public class Coordinador implements ICoordinador {

	/**
	 * Leera los datos de entrada del cliente y ejecutar� las iteraciones recibidas
	 * hasta que encuentre un resultado apropiado...
	 * @throws IOException 
	 * @throws ExecException 
	 * @throws Exception
	 */
	Path localPopulationFile=new Path("./population.txt");
	Path hdfsPopulationPath=new Path("/user/hadoop-user/input/population.txt");
	Path subOptimalResultsFilePath= new Path("/user/hadoop-user/output/part-r-00000");
	//Path pigResultFile= new Path("/user/hadoop-user/output/pigResults/part-00000");
	Hashtable<String, Integer> hTable = new Hashtable();
	
	
	@Override
	public String readFromClientAndIterate(int numPop, int maxiter, int debug, int boolElit, String numProblem, int endCriterial) throws IOException, ExecException, Exception {
		
		String bestIndividual="";
		String []args = new String[1];
		Configuration conf = new Configuration();
		FileSystem fs = FileSystem.get(conf);
		Path oldPopulationsDirPath = new Path ("/user/hadoop-user/oldPopulations");
		JobContext jCont = new JobContext(conf, null);	
		
		//Simulamos el criterio de fin de ejecución por consecución del objetivo con un numero de iteraciones muy elevado
		if (endCriterial == 1) {
			maxiter = 100000;
		}
			
		for (int i=0; i<maxiter; i++) {
			
			/**Si es la primera iteracion, subiremos la poblacion inicial, sino la de los
			 * descendientes. Si no es la primera iteracion tendra que ejecutar el codigo Pig para
		     * saber cual es la poblacion optima de la iteracion, almacenandola ya en el master... 
			 */
			Path currentPopulationFilePath = new Path ("/user/hadoop-user/input/population_"+i+".txt");
			System.out.println("COORDINADOR: La iteracion actual es la: "+i);

			//Si es la primera iteracion, leemos el fichero localmente...
			if (i==0) this.uploadToHDFS(jCont, localPopulationFile.toString());		
			
			System.out.println("COORDINADOR: Llamo al master");
			//Le paso los argumentos...
			args[0] = numProblem;
			//args[1] = Integer.toString(boolElit);
			MRPGAMaster.main(args);
			System.out.println("COORDINADOR: Acaba el master");
			
			/**Miramos si en la poblacion resultante tenemos el resultado objetivo... 
			 */
			System.out.println("COORDINADOR: BUSCO EL INDIVIDUO OBJETIVO");
			hTable = this.searchBestIndividual(subOptimalResultsFilePath);
			if (hTable.containsValue(0)) break;
			System.out.println("COORDINADOR: NO ENCUENTRO EL INDIVIDUO OBJETIVO");
			
			System.out.println("COORDINADOR: Llamo al script de Pig");		
			this.runPigScript(subOptimalResultsFilePath.toString(),i,conf);
			System.out.println("COORDINADOR: Acaba el script de Pig");
			
			//Si el parámetro "debug" está activado, vamos a crear un directorio nuevo en el que se van a ir colocando
			//todas las poblaciones, para poder ver su evolución...
			if (debug==1) {
				fs.mkdirs(oldPopulationsDirPath);
				Path targetFilePopPath = new Path ("/user/hadoop-user/oldPopulations/population_"+i+".txt");
				if (i==0) {
					//Movemos la poblacion inicial y la que obtiene Pig
					Path initialFilePopPath =  new Path("/user/hadoop-user/oldPopulations/population.txt");
					fs.rename(hdfsPopulationPath, initialFilePopPath);
					//Necesitamos una copia de la última población descendiente que sirva de entrada para la siguiente iteracion
					FileUtil.copy(fs, currentPopulationFilePath, fs, targetFilePopPath, false, conf);	
				}
					
				else {
					//Borramos el fichero de poblacion de la descendencia anterior...
					fs.delete(new Path("/user/hadoop-user/input/population_"+(i-1)+".txt"), true);
					//Copiamos el fichero como entrada de la siguiente iteracion...
					FileUtil.copy(fs, currentPopulationFilePath, fs, targetFilePopPath, false, conf);
				}
			}	
		}
		//Si no se introduce elitismo, imprimimos el mejor individuo que hayamos encontrado...
		System.out.println("COORDINADOR: Imprimo el mejor individuo...");
		bestIndividual = printBestIndividual(hTable.keys().toString(),(Integer)hTable.elements().nextElement());
		System.out.println("COORDINADOR: Acabo de imprimir el mejor individuo...");
	return bestIndividual;
	}

	/**
	 * Este metodo ejecuta codigo Pig Latin embebido en Java, de tal forma que recopile los
	 * distintos individuos sub-optimos que generen los "reduce" locales (que estaran almacenados
	 * en un formato de documento de texto) y aplique sobre ellos las operaciones que necesitemos
	 * (merge, order, filter y select -en principio...). Ademas le tendra que enviar el fichero
	 * resultante con la poblacion optima de la iteracion al Coordinador, para que este se la 
	 * pase al master y comience una nueva iteracion
	 * @author Alberto Luengo Cabanillas
	 *
	 */
	@Override
	public void runPigScript(String inputFile, int iteration, Configuration conf) throws ExecException, IOException {
		
		//Tenemos que leer un fichero del HDFS
		System.out.println("COORDINADOR: Dentro del script de Pig");
		FileSystem fs = FileSystem.get(conf);
		Path resultPath = new Path("pigResults");
	    
	    try {
	    	if (fs.exists(resultPath)) {
	    		//Borro el directorio con todo su contenido
	    		fs.delete(resultPath,true);
	    	}
	    }
	    catch (IOException ioe) {
	    	System.err.println("COORDINADOR:Se ha producido un Error borrando el dir de salida de Pig");
	    	System.exit(1);
	    }
		
	    String popIterationName = "input/population_"+iteration+".txt";
		
		PigServer pigServer = new PigServer("mapreduce");
		
		//PigContext pigContext = pigServer.getPigContext();
		//String jobName = "pigPopulation";
		//pigServer.getPigContext().getProperties().setProperty(PigContext.JOB_NAME,jobName);
		pigServer.registerQuery("raw_data = load '" + inputFile + "' using PigStorage('	');");
		pigServer.registerQuery("B = foreach raw_data generate $0 as id;");
		pigServer.registerQuery("store B into 'pigResults';");
		pigServer.renameFile("pigResults/part-00000", popIterationName);
		
		//Cuando acabo, borro el contenido del dir 'pigResults' (en el que sólo quedarán los logs...)
		fs.delete(resultPath,true);
		
		//pigServer.registerQuery("grouped = group raw_data by $0;");
		//pigServer.registerQuery("data = foreach grouped generate FLATTEN(group) as value;");
		//pigServer.registerQuery("full = foreach A generate $0 as id;");
		
	}
	@Override
	public void replacePopulationFile(Path originalPop, Path actualPopPath) throws IOException {
		//Leemos el fichero de poblacion que tenemos en el HDFS y lo reemplazamos
		//por el de la descendencia antes de entrar en la siguiente iteracion
		Configuration conf = new Configuration();
		FileSystem fs = FileSystem.get(conf);
		//Path populationPath = new Path("input/population.txt");
		//System.out.println("COORDINADOR: Dentro del replaceFile el originalPop es "+originalPop+" y el actualPop es "+actualPopPath);
		
	    try {
	    	if (fs.exists(originalPop)) {
	    		System.out.println("Dentro del replaceFile: Sí existe el originalPop...");
	    		//remove the file first
	    		fs.delete(originalPop,true);
	    		fs.rename(actualPopPath, hdfsPopulationPath);
	    	}
	    }
	    catch (IOException ioe) {
	    	System.err.println("COORDINADOR: Se ha producido un error reemplazando los ficheros");
	    	System.exit(1);
	    }
	}

	@Override
	public void uploadToHDFS(JobContext cont, String population) throws IOException {
		//Indicamos a que directorio del HDFS lo queremos subir...
		final String HDFS_POPULATION_FILE="/user/hadoop-user/input/population.txt";
		
		//Hacemos lo mismo con los ficheros de configuracion para los nodos worker...
		final String LOCAL_MAPPER_CONFIGURATION_FILE="./mapper_configuration.dat";
		//Indicamos a que directorio del HDFS lo queremos subir...
		final String HDFS_MAPPER_CONFIGURATION_FILE="/user/hadoop-user/data/mapper_configuration.dat";

		final String LOCAL_REDUCER_CONFIGURATION_FILE="./reducer_configuration.dat";
		//Indicamos a que directorio del HDFS lo queremos subir...
		final String HDFS_REDUCER_CONFIGURATION_FILE="/user/hadoop-user/data/reducer_configuration.dat";
		
		FileSystem fs = FileSystem.get(cont.getConfiguration());
		
		Path hdfsPopPath = new Path(HDFS_POPULATION_FILE);
		Path hdfsConfMapPath = new Path(HDFS_MAPPER_CONFIGURATION_FILE);
		Path hdfsConfRedPath = new Path(HDFS_REDUCER_CONFIGURATION_FILE);
		
		//subimos el fichero al HDFS del nodo master. Sobreescribimos cualquier copia.
		fs.copyFromLocalFile(false, true, new Path(population), hdfsPopPath);
		//Hacemos lo mismo con los ficheros de configuracion para poder distribuirlos...
		fs.copyFromLocalFile(false, true, new Path(LOCAL_MAPPER_CONFIGURATION_FILE), hdfsConfMapPath);
		fs.copyFromLocalFile(false, true, new Path(LOCAL_REDUCER_CONFIGURATION_FILE), hdfsConfRedPath);
		
		//Creamos el directorio para ir almacenando los mejores individuos de cada iteracion...
		fs.mkdirs(new Path("/user/hadoop-user/bestIndividuals"));
		
		//Mandamos el fichero de configuracion a todos los nodos...
		//DistributedCache.addCacheFile(hdfsConfMapPath.toUri(),cont.getConfiguration());
		//DistributedCache.addCacheFile(hdfsConfRedPath.toUri(),cont.getConfiguration());
	}

	@Override
	public String printBestIndividual(String bestIndividual, int bestFitness) {
		String result = "Best individual: '"+bestIndividual+"' with fitness: "+bestFitness+"";
		return result;
	}
	
	@Override
	public String readFromHDFS(String stringPath) {
		Path pathToRead = new Path(stringPath);
		FileSystem hdfs;
		String strLine = "", bestIndividual = "", result = "";
		try {
			hdfs = FileSystem.get(new Configuration());
			//Validamos primero el path de entrada antes de leer del fichero
			if (!hdfs.exists(pathToRead))
			{
				throw new IOException("El fichero especificado " +pathToRead.toString() + "no existe");
			}
			
			if (!hdfs.isFile(pathToRead))
			{
				throw new IOException("El fichero especificado "+pathToRead.toString() + "no existe");
			}
			FSDataInputStream dis = hdfs.open(pathToRead);
			BufferedReader br = new BufferedReader(new InputStreamReader(dis));
			
			while ((strLine = br.readLine()) != null)   {
				bestIndividual = strLine;
		      }
			dis.close();
			result = "Best individual: "+bestIndividual;
		
		} catch (IOException e) {
			result="ERROR READING FILE FROM HDFS:IOEXCEPTION";
		}
		return result;
	}

	@Override
	public Hashtable<String, Integer> searchBestIndividual(Path resultsPath) throws IOException {
		Hashtable hTable = new Hashtable();
		FileSystem hdfs = FileSystem.get(new Configuration());
		Scanner s = null;
	
		//Leo el fichero alojado en el HDFS
		//Validamos primero el path de entrada antes de leer del fichero
		if (!hdfs.exists(resultsPath))
		{
			throw new IOException("El fichero especificado " +resultsPath.toString() + "no existe");
		}
		
		if (!hdfs.isFile(resultsPath))
		{
			throw new IOException("El fichero especificado "+resultsPath.toString() + "no existe");
		}
		
		FSDataInputStream dis = hdfs.open(resultsPath);
		BufferedReader br = new BufferedReader(new InputStreamReader(dis));
		
	    try {
	    	s = new Scanner(br);
			while (s.hasNextLine()) {
				String linea = s.nextLine();
				Scanner sl = new Scanner(linea);
				/**La expresion regular que nos indica que nuestro delimitador es uno o
				 * varios espacios es \\s.
				 */
				sl.useDelimiter("\\s");
				/**Ahora metemos el primer elemento que encontramos (la palabra) como
				 * clave del Hashtable y el segundo (el fitness) como valor
				 */
				String keyWord = sl.next();
				String fitness = sl.next();
				int valor = Integer.parseInt(fitness);
				
				/**Hemos llegado al final*/
				if (valor==0) {
					hTable.put(keyWord, valor);
					break;
				}	
			}
	    }
	    catch(Exception e){
	    	e.printStackTrace();
	    }finally{
	    	/**En el finally cerramos el fichero, para asegurarnos
	    	 * que se cierra tanto si todo va bien como si salta 
	    	 * una excepcion.
	    	 */
	    	try{                    
	    		if( null != s ){   
	    			s.close();     
	    		}                  
	    	}catch (Exception e2){ 
	    		e2.printStackTrace();
	    	}
	    }
	return hTable;
	}
}
