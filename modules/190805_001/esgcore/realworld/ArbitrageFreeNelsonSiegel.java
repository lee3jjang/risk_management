package esgcore.realworld;

import java.util.function.Function;

import esgcore.filter.KalmanFilter;
import esgcore.linalg.Matrix;

import esgcore.linalg.Vector;
import esgcore.optim.NelderMead;
import esgcore.termstructure.SmithWilson;


public class ArbitrageFreeNelsonSiegel extends KalmanFilter {
	
	private Vector tau = new Vector(new double[] {0.25, 0.5, 0.75, 1., 1.5, 2., 2.5, 3., 5., 7., 10., 15., 20.});
	private Vector param;
	private double dt;
	private Vector maturity;
	
	public ArbitrageFreeNelsonSiegel(Vector param, double dt) {
		this.setParam(param, dt);
	}
	
	public void setParam(Vector param, double dt) {
		
		// Get parameters
		double lambda = param.getEntry(0);
		double eps = param.getEntry(1);
		double kappa1 = param.getEntry(2);
		double kappa2 = param.getEntry(3);
		double kappa3 = param.getEntry(4);
		double theta1 = param.getEntry(5);
		double theta2 = param.getEntry(6);
		double theta3 = param.getEntry(7);
		double sigma11 = param.getEntry(8);
		double sigma21 = param.getEntry(9);
		double sigma22 = param.getEntry(10);
		double sigma31 = param.getEntry(11);
		double sigma32 = param.getEntry(12);
		double sigma33 = param.getEntry(13);
		this.param = param;
		this.dt = dt;
		
		// Set States
		this.A = (new Vector(new double[] {1-kappa1*dt, 1-kappa2*dt, 1-kappa3*dt})).diag();
		this.u = new Vector(new double[] {kappa1*theta1*dt, kappa2*theta2*dt, kappa3*theta3*dt});
		Matrix S = new Matrix(new double[][] {{sigma11, 0., 0.}, {sigma21, sigma22, 0}, {sigma31, sigma32, sigma33}});
		this.Q = S.multiply(S.transpose()).scalarMultiply(dt);
		int m = tau.getDimension();
		this.H = Matrix.createZeroMatrix(m, 3);
		H.setColumnVector(0, tau.map(t -> 1.));
		H.setColumnVector(1, tau.map(t -> (1.-Math.exp(-lambda*t))/(lambda*t)));
		H.setColumnVector(2, tau.map(t -> (1.-Math.exp(-lambda*t))/(lambda*t)-Math.exp(-lambda*t)));
		this.R = Matrix.createIdentityMatrix(m).scalarMultiply(Math.pow(eps, 2));
		
		// Yield Adjustment Term
		double A_bar = sigma11*sigma11+sigma21*sigma21+sigma31*sigma31;
		double B_bar = sigma21*sigma21+sigma22*sigma22+sigma32*sigma32;
		double C_bar = sigma31*sigma31+sigma32*sigma32+sigma33*sigma33;
		double D_bar = sigma11*sigma21+sigma21*sigma22+sigma31*sigma32;
		double E_bar = sigma11*sigma31+sigma21*sigma32+sigma31*sigma33;
		double F_bar = sigma21*sigma31+sigma22*sigma32+sigma32*sigma33;
		this.g = tau.map(t ->
			A_bar*t*t/6
			+ B_bar*(0.5/Math.pow(lambda, 2) - 1/Math.pow(lambda, 3)*(1-Math.exp(-lambda*t))/t + 0.25/Math.pow(lambda, 3)*(1-Math.exp(-2*lambda*t))/t)
			+ C_bar*(0.5/Math.pow(lambda, 2) + 1/Math.pow(lambda, 2)*Math.exp(-lambda*t) - 0.25/lambda*t*Math.exp(-2*lambda*t) - 0.75/Math.pow(lambda, 2)*Math.exp(-2*lambda*t) - 2/Math.pow(lambda, 3)*(1-Math.exp(-lambda*t))/t + 0.625/Math.pow(lambda, 3)*(1-Math.exp(-2*lambda*t))/t)
			+ D_bar*(0.5/Math.pow(lambda, 2) + 1/Math.pow(lambda, 2)*Math.exp(-lambda*t) - 1/Math.pow(lambda, 3)*(1-Math.exp(-lambda*t))/t)
			+ E_bar*(3/Math.pow(lambda, 2)*Math.exp(-lambda*t) + 0.5/lambda*t + 1/lambda*t*Math.exp(-lambda*t) - 3/Math.pow(lambda, 3)*(1-Math.exp(-lambda*t))/t)
			+ F_bar*(1/Math.pow(lambda, 2) + 1/Math.pow(lambda, 2)*Math.exp(-lambda*t) - 0.5/Math.pow(lambda, 2)*Math.exp(-2*lambda*t) - 3/Math.pow(lambda, 3)*(1-Math.exp(-lambda*t))/t + 0.75/Math.pow(lambda, 3)*(1-Math.exp(-2*lambda*t))/t)
		);		
		
	}
	
	public void setMaturity(Vector maturity) {
		this.maturity = maturity;
	}
	
	public Vector estimate() {
		Vector x0 = this.param;
		Function<Vector, Double> fn = x -> {
			DynamicNelsonSiegel dns = new DynamicNelsonSiegel(x, this.dt);
			dns.setMeasurements(this.getMeasurements());
			dns.setState(this.getState(), this.getError());
			return dns.getLogLikelihood();
		};
		NelderMead nm = new NelderMead();
		return nm.optimize(fn, x0);
	}
	
	// Generate Spot Rates
	public Matrix generateSpotRate(int n, int t) {
		Matrix values = new Matrix(new double[n][1200]);
		Vector rates = new Vector(new double[n]);
		double ufr = 0.045;
		double llp = 20.;
		Vector mat = Vector.createRangeVector(1200).scalarAdd(1.).scalarMultiply(1./12.);
		for(int i=0; i<n; i++) {
			rates = this.generateMeasurement(t).getRowVector(t-1);
			SmithWilson sw = new SmithWilson(this.maturity, rates, Math.log(1+ufr), llp);
			values.setRowVector(i, sw.spot(mat));
		}
		return values;
	}
	
		
		
}
