import mpi.MPI;
import mpi.Status;

import java.text.NumberFormat;

public class Allreduce {

	public static void main(String[] args) throws Exception {
		aa(args);
	}

	public Allreduce() {
	}

	public static void aa(String[] args) throws Exception {

		double      startwtime, endwtime, pot2npesd;
		int         i, iterations, size, ns, my_pe, npes, ndoubles, pot2npes, pot2size;
		Status status;

		MPI.Init(args);

		my_pe = MPI.COMM_WORLD.Rank();
		npes  = MPI.COMM_WORLD.Size();

		MPI.COMM_WORLD.Barrier();

		iterations = 10000;

		size = 400;

		ndoubles = size/8;


		double A[] = new double [ndoubles];
		double B[] = new double [ndoubles];

		double timesBcast[] = new double [iterations];
		double timesBcastReduce[] = new double [iterations];

		for (i = 0; i < ndoubles; i++) {
			A[i] = (double) 1. / (i + 1);
		}

		pot2npes = (int)(Math.log(npes)/0.69314718);
		pot2npesd = (double)(Math.log(npes)/0.69314718);

		pot2size = (int)(Math.log(size)/1.386294361);
		if (pot2size < 0) pot2size = 0;


		for (ns = 0; ns < iterations; ns++) {
			MPI.COMM_WORLD.Barrier();
			startwtime = MPI.Wtime();
			MPI.COMM_WORLD.Allreduce(A, 0, B, 0, ndoubles, MPI.DOUBLE, MPI.MAX);
			endwtime = MPI.Wtime();

			timesBcast[ns] = (double) (1000000*(endwtime - startwtime));

		}

		MPI.COMM_WORLD.Reduce(timesBcast, 0, timesBcastReduce, 0, iterations, MPI.DOUBLE, MPI.MAX, 0);

		if (my_pe == 0) {
			//Format the Number to Display
			NumberFormat nf = NumberFormat.getInstance();
			nf.setMaximumFractionDigits(6);
			nf.setMinimumFractionDigits(6);

			for (i = 0; i < iterations; i++) {
				System.out.print(nf.format((double) timesBcastReduce[i]/1000000)+" \t "+(long) timesBcastReduce[i]+" \t "+nf.format((double)(size/(timesBcastReduce[i]))));
				System.out.println(" \t "+npes+" \t "+pot2npes+" \t "+nf.format(pot2npesd)+" \t "+size+" \t "+pot2size);
			}
		}
		if(my_pe == 0)
			System.out.println("Allreduce TEST COMPLETE");
		MPI.Finalize();

	}
}