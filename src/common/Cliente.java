package common;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 * Las funciones del cliente seran basicamente de introducir datos al sistema e inicializar
 * la poblacion a partir de esos datos:
 * 1.- Definir el tamanho de la poblacion
 * 2.- Definir el total de iteraciones
 * 3.- Definir el grado de elitismo en la descendencia
 * 4.- Definir el grado de mutacion
 * 5.- Definir cual es la frase objetivo
 * 
 */

public class Cliente extends Configured implements Tool {

	
	private static String generateRandomString(String target) {
			String individuo = "";
			int sizeTarget=target.length();
			Random r = new Random(System.nanoTime());
			char spanish_chars[]={'!','¡','.','¿','?','_',',',';','á','é','í','ó','ú'};
			Arrays.sort(spanish_chars);
			int i=0;
			int position = 0;
				while ( i < sizeTarget){
					char c = (char)r.nextInt(255);
					position = Arrays.binarySearch(spanish_chars, c);
					if((c>='0' && c<='9') || (c>='a' && c<='z') || (c>='A' && c<='Z') || (position > 0))
					{
						individuo += c;
						i ++;
					}
				}
			return individuo;
			}
		
	private static String generateRandomBinaryString(int geneNumber) {
		String cadenaAleatoria ="";
		int cont=0;
		double prob =0.0;
			while (cont < geneNumber)
			{
				prob = java.lang.Math.random();
				if ((prob <= 0.5))
					cadenaAleatoria += '0';
				else
					cadenaAleatoria += '1';
				cont ++;
			}
		return cadenaAleatoria;
		}
	
	
	private static void generatePopulationFile(String target, int sizePop, int geneNumber, int numProblem) {
		
		//Instanciamos el fichero...
		String sFile ="population.dat";
		File initPop = new File("./",sFile);
		//Miramos si existe...
		if (initPop.exists()){
			System.out.println("CLIENTE:Regenerando fichero de poblacion...");
			initPop.delete();
		}
		else
		{
			//Creamos el fichero....
			try {
				// A partir del objeto File creamos el fichero fisicamente
				if (initPop.createNewFile())
					System.out.println("CLIENTE:El fichero de poblacion ha sido creado correctamente!");
				else
					System.out.println("CLIENTE:El fichero de poblacion no ha podido ser creado...");
			}catch (IOException e){
				e.printStackTrace();
			}
		}
		//Escribimos en el fichero previamente creado...
		try {
			BufferedWriter bw= new BufferedWriter(new FileWriter(sFile));
			System.out.println("CLIENTE: Escribiendo en fichero de poblacion...");
			String word="";
			int i=0;
			while (i <sizePop) {
				if (numProblem == 1) //Caso del problem de la 'frase objetivo'
					//Generamos la palabra descendiente...
					word=generateRandomString(target);
				else
					//Generamos el individuo binario...
					word=generateRandomBinaryString(geneNumber);
				//Escribimos a fichero...	
				bw.write(word +"\n");
				i++;
			}
			//Cerramos el fichero
			System.out.println("CLIENTE: Cerrando fichero de poblacion...");
			bw.close();
		} catch (IOException e){e.printStackTrace();}
	}
	
	//Creamos el fichero de configuracion que debe subir el coordinador al HDFS para que el
	//Master lo distribuya entre los nodos trabajadores...
	private static void generateMapperConfigurationFile(String target, int numPop, int boolElit, int debug,int gene_length) {
		
		//Instanciamos el fichero...
		String sFile ="mapper_configuration.dat";
		File initPop = new File("./",sFile);
		//Miramos si existe...
		if (initPop.exists()){
			System.out.println("CLIENTE: Regenerando fichero de configuracion para Mappers...");
			initPop.delete();
		}
		else
		{
			//Creamos el fichero....
			try {
				// A partir del objeto File creamos el fichero fisicamente
				if (initPop.createNewFile())
					System.out.println("CLIENTE: El fichero de configuracion para Mappers ha sido creado correctamente!");
				else
					System.out.println("CLIENTE: El fichero de configuracion para Mappers no ha podido ser creado...");
			}catch (IOException e){
				e.printStackTrace();
			}
		}
		//Escribimos en el fichero previamente creado
		try {
			BufferedWriter bw= new BufferedWriter(new FileWriter(sFile));
			System.out.println("CLIENTE: Escribiendo en fichero de configuracion para Mappers...");
			bw.write(target +"\r\n");
			bw.write(numPop +"\r\n");
			bw.write(boolElit +"\r\n");
			bw.write(debug +"\r\n");
			bw.write(gene_length +"\r\n");
			
			//Cerramos el fichero
			System.out.println("CLIENTE: Cerrando fichero de configuracion para Mappers...");
			bw.close();
		} catch (IOException e){e.printStackTrace();}	
	}
	
	//Creamos el fichero de configuracion que debe subir el coordinador al HDFS para que el
	//Master lo distribuya entre los nodos trabajadores...
	private static void generateReducerConfigurationFile(int numpop, int maxiter, int boolElit,
			float mutationrate, int mutation, double crossProb, String target) {
		//Instanciamos el fichero...
		String sFile ="reducer_configuration.dat";
		File initPop = new File("./",sFile);
		//Miramos si existe...
		if (initPop.exists()){
			System.out.println("CLIENTE: Regenerando fichero de configuracion para Reducers...");
			initPop.delete();
		}
		else
		{
			//Creamos el fichero....
			try {
				// A partir del objeto File creamos el fichero fisicamente
				if (initPop.createNewFile())
					System.out.println("CLIENTE: El fichero de configuracion para Reducers ha sido creado correctamente!");
				else
					System.out.println("CLIENTE: El fichero de configuracion para Reducers no ha podido ser creado...");
			}catch (IOException e){
				e.printStackTrace();
			}
		}
		//Escribimos en el fichero previamente creado
		try {
			BufferedWriter bw= new BufferedWriter(new FileWriter(sFile));
			System.out.println("CLIENTE: Escribiendo en fichero de configuracion para Reducers...");
			bw.write(numpop +"\r\n");
			bw.write(maxiter +"\r\n");
			bw.write(boolElit +"\r\n");
			bw.write(mutationrate +"\r\n");
			bw.write(mutation +"\r\n");
			bw.write(crossProb +"\r\n");
			bw.write(target);
			//Cerramos el fichero
			System.out.println("CLIENTE: Cerrando fichero de configuracion para Reducers...");
			bw.close();
		} catch (IOException e){e.printStackTrace();}	
	}
	
	
	void launch(String numProblem, int maxIter, int population, int geneNumber, double crossProb, int boolElit, int mutation, int debug, int endCriterial, String targetPhrase) {
		
		Configuration conf = new Configuration();
		FileSystem fs = null;
		try {
			fs = FileSystem.get(conf);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String users = conf.get("hadoop.job.ugi");
		String[] commas = users.split(",");
		String userName = commas[0];
		String result ="";
	
		//Como se van a generar individuos con un numero de genes igual a la longitud
		//de la palabra objetivo, su posibilidad de mutación será su inversa...
		float mutationrate= 1.0f/(float)targetPhrase.length();
		Coordinador coord = new Coordinador(userName);
		
		/**
		 * PASO 1.- Generamos la poblacion inicial y los ficheros de configuracion 
		 * para los nodos Worker... 
		 */
		generatePopulationFile(targetPhrase,population,geneNumber,Integer.parseInt(numProblem));
		generateMapperConfigurationFile(targetPhrase, population, boolElit, debug,geneNumber);
		generateReducerConfigurationFile(population, maxIter, boolElit, mutationrate, mutation, crossProb, targetPhrase); 
		

		/**
		 * PASO 2.- El coordinador realizara las iteraciones pertinentes y devolvera el resultado buscado...
		 */
		System.out.println("CLIENTE: Lanzando trabajo...");
        final long startTime = System.currentTimeMillis();
		try {
			result = coord.readFromClientAndIterate(population, maxIter, debug, boolElit, numProblem, endCriterial,geneNumber);
		} catch (IOException e) {
			System.err.println("CLIENTE: Se ha producido un error de I/O en la conexion al HDFS");
		}catch (Exception e) {
		// TODO Auto-generated catch block
		System.err.println("CLIENTE: Se ha producido un error generico ejecutando el codigo del Master");
		}
		System.out.println("CLIENTE: "+ result);
		final double duration = (System.currentTimeMillis() - startTime)/1000.0;
	    System.out.println("CLIENTE: Trabajo finalizado en " + duration + " segundos");
		System.out.println("****FIN DE EJECUCION****");
		
	}

	@Override
	public int run(String[] args) throws Exception {
		if (args.length < 9) {
			System.err.println("***********************************");
			System.err.println("Uso: "+ getClass().getName()+" <numProblem> <nIterations> <sizePop> <geneNumber> <crossProb> <boolElit> <mutation> <debug> <endCriterial> [<targetPhrase>]");
			System.err.println("***********************************");
			ToolRunner.printGenericCommandUsage(System.err);
			System.err.println("***********************************");
			return -1;
		}
		String numProblem = args[0];
		System.out.println("NUMPROBLEM: "+numProblem);
		int	numIterations = Integer.parseInt(args[1]);
		System.out.println("NUM ITERATIONS: "+numIterations);
		int sizePop = Integer.parseInt(args[2]);
		System.out.println("POPULATION: "+sizePop);
		int geneNumber = Integer.parseInt(args[3]);
		System.out.println("GENE NUMBER: "+geneNumber);
		double crossProb = Double.parseDouble(args[4]);
		System.out.println("CROSSPROB: "+crossProb);
		int boolElit = Integer.parseInt(args[5]);
		System.out.println("ELITISM?: "+boolElit);
		int mutation = Integer.parseInt(args[6]);
		System.out.println("MUTATION?: "+mutation);
		int debug = Integer.parseInt(args[7]);
		System.out.println("DEBUG?: "+debug);
		int endCriterial = Integer.parseInt(args[8]);
		System.out.println("END CRITERIAL: "+endCriterial);
		String target_phrase = "";
		try {
			target_phrase = args[9];
		}
		catch (ArrayIndexOutOfBoundsException e){
			target_phrase = "Hello_world!";
		}
		System.out.println("TARGET PHRASE: "+target_phrase);
		launch(numProblem, numIterations, sizePop, geneNumber, crossProb, boolElit, mutation, debug, endCriterial, target_phrase);
		return 0;
	}
	
	public static void main(String[] argv) throws Exception {
		int res = ToolRunner.run(new Configuration(), new Cliente(), argv);
	}

}
