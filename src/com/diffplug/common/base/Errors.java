/**
 * Copyright 2015 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.common.base;

import java.io.PrintWriter;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/** 
 * Errors makes it easy to create implementations of the standard
 * functional interfaces (which don't allow checked exceptions).
 * 
 * Even for cases where you aren't required to stuff some code into a
 * functional interface, Errors is useful as a concise way to
 * specify how errors will be handled. 
 */
public abstract class Errors {
	/** Package-private for testing - resets all of the static member variables. */
	static void resetForTesting() {
		log = null;
		dialog = null;
	}

	protected final Consumer<Throwable> handler;

	/**
	 * Creates an Errors.Rethrowing which transforms any exceptions it receives into a RuntimeException
	 * as specified by the given function, and then throws that RuntimeException.
	 * 
	 * If that function happens to throw an unchecked error itself, that'll work just fine.
	 */
	public static Rethrowing createRethrowing(Function<Throwable, RuntimeException> transform) {
		return new Rethrowing(transform);
	}

	/**
	 * Creates an Errors.Handling which passes any exceptions it receives
	 * to the given handler.
	 * 
	 * The handler is free to throw a RuntimeException if it wants to. If it always
	 * throws a RuntimeException, then you should instead create an Errors.Rethrowing
	 * using creeateRethrowAs().
	 */
	public static Handling createHandling(Consumer<Throwable> handler) {
		return new Handling(handler);
	}

	protected Errors(Consumer<Throwable> error) {
		this.handler = error;
	}

	/** Suppresses errors entirely. */
	public static Handling suppress() {
		return suppress;
	}

	private static final Handling suppress = createHandling(Consumers.doNothing());

	/** Rethrows any exceptions as runtime exceptions. */
	public static Rethrowing rethrow() {
		return rethrow;
	}

	private static final Rethrowing rethrow = createRethrowing(Errors::asRuntime);

	/**
	 * Logs any exceptions.
	 * 
	 * By default, log() calls Throwable.printStackTrace(). To modify this behavior
	 * in your application, call DurianPlugins.set(Errors.Plugins.Log.class, error -> myCustomLog(error));
	 */
	@SuppressFBWarnings(value = "LI_LAZY_INIT_STATIC", justification = "This race condition is fine, as explained in the comment below.")
	public static Handling log() {
		if (log == null) {
			// There is an acceptable race condition here - log might get set multiple times.
			// This would happen if multiple threads called log() at the same time
			// during initialization, and this is likely to actually happen in practice.
			// 
			// Because DurianPlugins guarantees that its methods will have the exact same
			// return value for the duration of the library's runtime existence, the only
			// adverse symptom of this race condition is that there will temporarily be
			// multiple instances of Errors which are wrapping the same Consumer<Throwable>.
			//
			// It is important for this method to be fast, so it's better to accept
			// that suppress() might return different Errors instances which are wrapping
			// the same actual Consumer<Throwable>, rather than to incur the cost of some
			// type of synchronization.
			log = createHandling(DurianPlugins.get(Plugins.Log.class, Plugins::defaultLog));
		}
		return log;
	}

	private static Handling log;

	/**
	 * Opens a dialog to notify the user of any exceptions.  It should be used in cases where
	 * an error is too severe to be silently logged.
	 * 
	 * By default, dialog() opens a JOptionPane. To modify this behavior in your application,
	 * call DurianPlugins.set(Errors.Plugins.Dialog.class, error -> openMyDialog(error));
	 * 
	 * For a non-interactive console application, a good implementation of would probably
	 * print the error and call System.exit().
	 */
	@SuppressFBWarnings(value = "LI_LAZY_INIT_STATIC", justification = "This race condition is fine, as explained in the comment below.")
	public static Handling dialog() {
		if (dialog == null) {
			// There is an acceptable race condition here.  See Errors.log() for details.
			dialog = createHandling(DurianPlugins.get(Plugins.Dialog.class, Plugins::defaultDialog));
		}
		return dialog;
	}

	private static Handling dialog;

	/** Passes the given error to be handled by the Errors. */
	public void handle(Throwable error) {
		handler.accept(error);
	}

	/** Attempts to run the given runnable. */
	public void run(Throwing.Runnable runnable) {
		wrap(runnable).run();
	}

	/** Returns a Runnable whose exceptions are handled by this Errors. */
	public Runnable wrap(Throwing.Runnable runnable) {
		return () -> {
			try {
				runnable.run();
			} catch (Throwable e) {
				handler.accept(e);
			}
		};
	}

	/** Returns a Consumer whose exceptions are handled by this Errors. */
	public <T> Consumer<T> wrap(Throwing.Consumer<T> consumer) {
		return val -> {
			try {
				consumer.accept(val);
			} catch (Throwable e) {
				handler.accept(e);
			}
		};
	}

	/**
	 * An Errors which is free to rethrow the exception, but it might not.
	 * 
	 * If we want to wrap a method with a return value, since the handler might
	 * not throw an exception, we need a default value to return.
	 */
	public static class Handling extends Errors {
		protected Handling(Consumer<Throwable> error) {
			super(error);
		}

		/** Attempts to call the given supplier, returns onFailure if there is a failure. */
		public <T> T getWithDefault(Throwing.Supplier<T> supplier, T onFailure) {
			return wrapWithDefault(supplier, onFailure).get();
		}

		/** Attempts to call the given supplier, and returns the given value on failure. */
		public <T> Supplier<T> wrapWithDefault(Throwing.Supplier<T> supplier, T onFailure) {
			return () -> {
				try {
					return supplier.get();
				} catch (Throwable e) {
					handler.accept(e);
					return onFailure;
				}
			};
		}

		/** Attempts to call the given function, and returns the given value on failure. */
		public <T, R> Function<T, R> wrapWithDefault(Throwing.Function<T, R> function, R onFailure) {
			return input -> {
				try {
					return function.apply(input);
				} catch (Throwable e) {
					handler.accept(e);
					return onFailure;
				}
			};
		}

		/** Attempts to call the given function, and returns the given value on failure. */
		public <T> Predicate<T> wrapWithDefault(Throwing.Predicate<T> function, boolean onFailure) {
			return input -> {
				try {
					return function.test(input);
				} catch (Throwable e) {
					handler.accept(e);
					return onFailure;
				}
			};
		}
	}

	/**
	 * An Errors which is guaranteed to always throw a RuntimeException.
	 * 
	 * If we want to wrap a method with a return value, it's pointless to specify
	 * a default value because if the wrapped method fails, a RuntimeException is
	 * guaranteed to throw.
	 */
	public static class Rethrowing extends Errors {
		private final Function<Throwable, RuntimeException> transform;

		protected Rethrowing(Function<Throwable, RuntimeException> transform) {
			super(error -> {
				throw transform.apply(error);
			});
			this.transform = transform;
		}

		/** Attempts to call the given supplier, throws some kind of RuntimeException on failure. */
		public <T> T get(Throwing.Supplier<T> supplier) {
			return wrap(supplier).get();
		}

		/** Attempts to call the given supplier, throws some kind of RuntimeException on failure. */
		public <T> Supplier<T> wrap(Throwing.Supplier<T> supplier) {
			return () -> {
				try {
					return supplier.get();
				} catch (Throwable e) {
					throw transform.apply(e);
				}
			};
		}

		/** Attempts to call the given function, throws some kind of RuntimeException on failure. */
		public <T, R> Function<T, R> wrap(Throwing.Function<T, R> function) {
			return arg -> {
				try {
					return function.apply(arg);
				} catch (Throwable e) {
					throw transform.apply(e);
				}
			};
		}

		/** Attempts to call the given function, throws some kind of RuntimeException on failure. */
		public <T> Predicate<T> wrap(Throwing.Predicate<T> predicate) {
			return arg -> {
				try {
					return predicate.test(arg);
				} catch (Throwable e) {
					throw transform.apply(e); // 1 855 548 2505
				}
			};
		}
	}

	/** Converts the given exception to a RuntimeException, with a minimum of new exceptions to obscure the cause. */
	public static RuntimeException asRuntime(Throwable e) {
		if (e instanceof RuntimeException) {
			return (RuntimeException) e;
		} else {
			return new RuntimeException(e);
		}
	}

	/**
	 * Namespace for the plugins which Errors supports. Call
	 * DurianPlugins.register(Errors.Plugins.Log.class, logImplementation)
	 * if you'd like to change the default behavior.
	 */
	public interface Plugins {
		/** Errors.log(). */
		public interface Log extends Consumer<Throwable> {}

		/** Errors.dialog(). */
		public interface Dialog extends Consumer<Throwable> {}

		/** Default behavior of Errors.log() is Throwable.printStackTrace(). */
		static void defaultLog(Throwable error) {
			error.printStackTrace();
		}

		/** Default behavior of Errors.dialog() is JOptionPane.showMessageDialog without a parent. */
		static void defaultDialog(Throwable error) {
			SwingUtilities.invokeLater(() -> {
				error.printStackTrace();
				String title = error.getClass().getSimpleName();
				JOptionPane.showMessageDialog(null, error.getMessage() + "\n\n" + StringPrinter.buildString(printer -> {
					PrintWriter writer = printer.toPrintWriter();
					error.printStackTrace(writer);
					writer.close();
				}), title, JOptionPane.ERROR_MESSAGE);
			});
		}

		/**
		 * An implementation of all of the Errors built-ins which throws an AssertionError
		 * on any exception.  This can be helpful for JUnit tests.
		 */
		public static class OnErrorThrowAssertion implements Log, Dialog {
			@Override
			public void accept(Throwable error) {
				throw new AssertionError(error);
			}
		}
	}
}