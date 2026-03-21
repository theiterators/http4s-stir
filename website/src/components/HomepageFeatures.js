import React from 'react';
import clsx from 'clsx';
import styles from './HomepageFeatures.module.css';

const FeatureList = [
  {
    title: 'Familiar DSL',
    description: (
      <>
        If you know Pekko HTTP or Akka HTTP, you already know http4s-stir.
        Use the same directives, path matchers, and route composition you're used to.
      </>
    )
  },
  {
    title: 'Powered by cats-effect',
    description: (
      <>
        Built on http4s and cats-effect IO. Get the ergonomics of Pekko HTTP's DSL
        with the power of the Typelevel stack.
      </>
    ),
  },
  {
    title: 'Cross-platform',
    description: (
      <>
        Runs on JVM, Scala.js, and Scala Native.
        Supports both Scala 2.13 and Scala 3.
      </>
    ),
  },
];


function Feature({title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center padding-horiz--md">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
