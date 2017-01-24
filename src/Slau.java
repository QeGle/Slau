import mpi.MPI;

import java.io.IOException;
import java.util.Arrays;

public class Slau {
	static int elementsOnProc;
	static int rowsOnProc;
	private static int size;
	private static int rank;
	//max matrixSize=4220 при расылке матрицы по частям
	//               4000 при пересылке матрицы полностью
	private static int matrixSize = 4000;
	private static double e = 0.0001;

	public static void main(String[] args) throws IOException {
		MPI.Init(args);
		/*for (int x = 200; x <= 4000; x += 200) {
			matrixSize = x;
			if (rank == 0)
				System.out.println("matrixSize" + matrixSize);
			for (int count = 0; count < 10; count++) {*/

				rank = MPI.COMM_WORLD.Rank();
				size = MPI.COMM_WORLD.Size();
				double[] b = new double[matrixSize];
				double[] A = new double[matrixSize * matrixSize];
				rowsOnProc = matrixSize / size;
				elementsOnProc = matrixSize * rowsOnProc;
				//инициализируем на нулевом проце
				if (rank == 0)
					for (int i = 0; i < matrixSize; i++) {
						b[i] = 2;
						for (int j = 0; j < matrixSize; j++) {
							A[i * matrixSize + j] = 1;
							if (i == j)
								A[i * matrixSize + j] = matrixSize + i;
						}
					}

				//рассылка данных на все процы
				MPI.COMM_WORLD.Bcast(b, 0, matrixSize, MPI.DOUBLE, 0);
				MPI.COMM_WORLD.Bcast(A, 0, matrixSize*matrixSize, MPI.DOUBLE, 0);
				double time = 0;
				if (rank == 0) {
					time = System.currentTimeMillis();
				}

				//основной метод
				SoprGrad(A, b);


				//вывод служебной инфы
				if (rank == 0) {
					time = System.currentTimeMillis() - time;
					//System.out.println(count);
					System.out.println("size = " + size);
					System.out.println("time = " + time);
					//System.out.println(time);
					//System.out.println(matrixSize);
				}


		/*	}
		}*/
		MPI.Finalize();
	}

	//на вход матрица A и вектор b
	static void SoprGrad(double[] A, double[] b) throws IOException {


		int k = 0;
		double[] x = new double[matrixSize];
		double[] q = new double[matrixSize];
		double[] r = new double[matrixSize];
		double[] p = new double[matrixSize];

		double a, ro0, ro, ro1, beta;
		for (int i = 0; i < matrixSize; i++) {
			x[i] = 0;
		}

		System.arraycopy(b, 0, r, 0, matrixSize);
		System.arraycopy(r, 0, p, 0, matrixSize);

		ro0 = ro = Scal(r, r);
		do {
			MatrixByVectorWithoutSend(A, p, q);
			//MatrixByVector(A, p, q);

			a = ro / Scal(p, q);
			for (int i = 0; i < matrixSize; i++) {

				x[i] += (a * p[i]);
				r[i] -= a * q[i];
			}
			ro1 = Scal(r, r);
			beta = ro1 / ro;

			for (int i = 0; i < matrixSize; i++) {
				p[i] = r[i] - beta * p[i];
			}
			ro = ro1;
			k++;

		} while ((ro / ro0) >= (e * e));

		if (rank == 0) {
			System.out.println("k = " + k);
			System.out.println(Arrays.toString(x));
		}
	}

	//Матрично векторное произведение с рассылкой матрицы по частям
	static void MatrixByVector(double[] A, double[] b, double[] res) {
		double[] C = new double[matrixSize];
		for (int j = 0; j < matrixSize; j++) {
			C[j] = 0;
			res[j] = 0;
		}

		double[] bufA = new double[elementsOnProc];


		if (size > 1) {
			MPI.COMM_WORLD.Scatter(A, 0, elementsOnProc, MPI.DOUBLE, bufA, 0, elementsOnProc, MPI.DOUBLE, 0);
			//параллельный алгоритм
			for (int i = 0; i < matrixSize; i++) {
				for (int j = 0; j < rowsOnProc; j++) {
					C[j + rowsOnProc * rank] += bufA[i + j * matrixSize] * b[i];
				}
			}
			MPI.COMM_WORLD.Allreduce(C, 0, res, 0, matrixSize, MPI.DOUBLE, MPI.SUM);
		} else {
			//последовательный алгоритм
			for (int i = 0; i < matrixSize; i++) {
				for (int j = 0; j < matrixSize; j++) {
					res[i] += A[i * matrixSize + j] * b[j];
				}
			}
		}
	}

	//скалярное произведение
	static double Scal(double[] b, double[] c) {
		double[] res = {0};
		double[] Sum = {0};
		int flag = 0;
		int i1, i2, k;
		if (size > 1) {
			//параллельный алгоритм
			//на каждом проце суммируются произведения
			//элементов векторов b и c от i1 до i2
			k = matrixSize / size;

			//делится ли нацело количество элементов
			//в матрице на количество процов
			if (matrixSize % size != 0)
				flag = 1;
			i2 = k * (rank + 1);
			i1 = k * rank;
			//для того, чтобы не терять лишние элементы
			if ((rank == size - 1) && (flag == 1))
				i2 = matrixSize;
			for (int i = i1; i < i2; i++) {
				Sum[0] = Sum[0] + b[i] * c[i];
			}
			MPI.COMM_WORLD.Allreduce(Sum, 0, res, 0, 1, MPI.DOUBLE, MPI.SUM);
		} else {
			//последовательный алгоритм
			for (int i = 0; i < matrixSize; i++) {
				res[0] += b[i] * c[i];
			}
		}

		return res[0];
	}

	//Матрично векторное произведение без рассылки матрицы
	static void MatrixByVectorWithoutSend(double[] A, double[] b, double[] res) {
		double[] C = new double[matrixSize];
		for (int j = 0; j < matrixSize; j++) {
			C[j] = 0;
			res[j] = 0;
		}

		if (size > 1) {
			//параллельный алгоритм
			for (int i = 0; i < matrixSize; i += size) {
				for (int j = 0; j < matrixSize; j++) {
					C[rank + i] += A[(rank + i) * matrixSize + j] * b[j];
				}
			}
			MPI.COMM_WORLD.Allreduce(C, 0, res, 0, matrixSize, MPI.DOUBLE, MPI.SUM);
		} else {
			//последовательный алгоритм
			for (int i = 0; i < matrixSize; i++) {
				for (int j = 0; j < matrixSize; j++) {
					res[i] += A[i * matrixSize + j] * b[j];
				}
			}
		}
	}
}
